package com.shilapi.xcertplay.mfi

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERSequence

/** Enabled only by the explicit native-authentication APK build, never by vehicle autodetection. */
object ArcfoxNativeMfiDeployment {
    fun enabled(context: Context): Boolean {
        val id = context.resources.getIdentifier("arcfox_native_mfi", "bool", context.packageName)
        return id != 0 && context.resources.getBoolean(id)
    }
}

/** Only MID AA (authentication) is registered. No handshake/phone/media/source-control messages. */
internal class ArcfoxMfiWire(
    input: InputStream,
    output: OutputStream,
    private val finish: () -> Unit,
) : Closeable {
    private val input = DataInputStream(input)
    private val output = DataOutputStream(output)
    @Volatile private var closed = false

    fun register() = write(byteArrayOf(0x5b, 0, 0xaa.toByte()))

    @Synchronized fun request(action: Int, challenge: ByteArray = byteArrayOf()): ByteArray {
        check(!closed) { "Native MFi transport is closed" }
        require(action == 1 || action == 2)
        require(if (action == 1) challenge.isEmpty() else challenge.size in 1..32)
        try {
            val body = java.io.ByteArrayOutputStream()
            DataOutputStream(body).use {
                it.writeByte(0xaa); it.writeByte(action)
                if (action == 2) { it.writeInt(challenge.size); it.write(challenge) }
            }
            write(body.toByteArray())
            // Only the registered authentication family should be delivered. Unexpected action
            // fails closed: there is no request ID in this OEM protocol, so don't reuse it.
            val response = read()
            if (response.size < 6 || response[0] != 0xaa.toByte() || response[1].toInt() != action)
                throw IOException("Unexpected native MFi response action")
            val data = DataInputStream(response.copyOfRange(2, response.size).inputStream())
            val size = data.readInt()
            if (size !in 1..MAX_DATA || size != response.size - 6)
                throw IOException("Invalid native MFi response size")
            return ByteArray(size).also(data::readFully)
        } catch (error: Exception) { close(); throw error }
    }

    private fun write(body: ByteArray) {
        output.writeByte(0x5a); output.writeByte(0xa5); output.writeByte(1)
        output.writeInt(body.size + 7); output.write(body); output.flush()
    }

    private fun read(): ByteArray {
        if (input.readUnsignedByte() != 0x5a || input.readUnsignedByte() != 0xa5 ||
            input.readUnsignedByte() != 1) throw IOException("Invalid native MFi frame header")
        val size = input.readInt()
        if (size !in 9..(MAX_DATA + 13)) throw IOException("Invalid native MFi frame size")
        return ByteArray(size - 7).also(input::readFully)
    }

    // Closing must interrupt a blocked reader; it deliberately does not acquire request's lock.
    override fun close() { closed = true; runCatching(finish) }

    companion object { const val MAX_DATA = 16 * 1024 }
}

/** Uses the OEM mfiserver and chip, without loading vendor libraries or exporting a private key. */
class ArcfoxNativeMfi private constructor(
    private val wire: ArcfoxMfiWire,
    private val certificate: ByteArray,
    private val leaf: X509Certificate,
    private val major: Int,
) : MfiAuthenticator, Closeable {
    override fun protocolMajor(): Int = major

    override fun readCertificate(maximumOutputLength: Int): ByteArray {
        require(maximumOutputLength > 0 && certificate.size <= maximumOutputLength) {
            "Native MFi certificate exceeds the requested limit"
        }
        return certificate.copyOf()
    }

    @Synchronized override fun signChallenge(challenge: ByteArray): ByteArray {
        require(challenge.size == if (major == 2) 20 else 32) { "Invalid native MFi digest length" }
        try {
            val result = wire.request(2, challenge.copyOf())
            val verifier = if (major == 2) Signature.getInstance("NONEwithRSA") else Signature.getInstance("NONEwithECDSA")
            verifier.initVerify(leaf.publicKey)
            // MFi v2 RSA signs the SHA-1 DigestInfo; callers already supplied the SHA-1 digest.
            verifier.update(if (major == 2) SHA1_DIGEST_INFO + challenge else challenge)
            val signature = if (major == 2) result else ecDer(result)
            if (!verifier.verify(signature)) throw IOException("Native MFi signature did not match this challenge/certificate")
            return if (major == 2) result else LocalMfiAuthenticationClient.derToRaw(signature)
        } catch (error: Exception) { close(); throw error }
    }

    override fun close() = wire.close()

    companion object {
        private val SHA1_DIGEST_INFO = byteArrayOf(0x30,0x21,0x30,0x09,0x06,0x05,0x2b,0x0e,0x03,0x02,0x1a,0x05,0x00,0x04,0x14)
        private const val P256_ORDER = "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551"

        fun open(): ArcfoxNativeMfi {
            val socket = LocalSocket()
            try {
                socket.soTimeout = 5_000
                socket.connect(LocalSocketAddress("com.adayo.service.mfi", LocalSocketAddress.Namespace.ABSTRACT))
                val wire = ArcfoxMfiWire(socket.inputStream, socket.outputStream) { socket.close() }
                return open(wire)
            } catch (error: Exception) { socket.close(); throw error }
        }

        internal fun open(wire: ArcfoxMfiWire): ArcfoxNativeMfi {
            try {
                wire.register()
                val certificate = wire.request(1)
                val certificates = CertificateFactory.getInstance("X.509")
                    .generateCertificates(certificate.inputStream()).map { it as X509Certificate }
                val leaf = certificates.filter { it.basicConstraints < 0 }.singleOrNull()
                    ?: throw IOException("Expected one native accessory leaf certificate")
                val major = when (val key = leaf.publicKey) {
                    is RSAPublicKey -> {
                        require(key.modulus.bitLength() == 1024) { "Unsupported native MFi RSA key size" }
                        2
                    }
                    is ECPublicKey -> {
                        require(key.params.order.toString(16) == P256_ORDER) { "Unsupported native MFi EC curve" }
                        3
                    }
                    else -> throw IOException("Unsupported native MFi certificate key")
                }
                val client = ArcfoxNativeMfi(wire, certificate, leaf, major)
                // Establish digest/signature compatibility before announcing MFiReady. Every later
                // signature is verified too, so a stale/other-client reply cannot be accepted.
                val probe = ByteArray(32).also(SecureRandom()::nextBytes)
                client.signChallenge(MessageDigest.getInstance(if (major == 2) "SHA-1" else "SHA-256").digest(probe))
                return client
            } catch (error: Exception) { wire.close(); throw error }
        }

        private fun ecDer(signature: ByteArray): ByteArray = if (signature.size == 64) {
            DERSequence(arrayOf(
                ASN1Integer(BigInteger(1, signature.copyOfRange(0, 32))),
                ASN1Integer(BigInteger(1, signature.copyOfRange(32, 64))),
            )).encoded
        } else signature
    }
}
