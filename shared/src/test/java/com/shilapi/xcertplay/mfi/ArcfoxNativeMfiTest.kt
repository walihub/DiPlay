package com.shilapi.xcertplay.mfi

import java.io.*
import java.math.BigInteger
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.util.Date
import org.bouncycastle.asn1.*
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.junit.Assert.*
import org.junit.Test

class ArcfoxNativeMfiTest {
    private class Chip(val rsa: Boolean = false, val rawEc: Boolean = false) {
        val pair = KeyPairGenerator.getInstance(if (rsa) "RSA" else "EC").apply {
            if (rsa) initialize(1024) else initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val cert: ByteArray
        val frames = mutableListOf<ByteArray>()
        val queue = java.util.ArrayDeque<Int>()
        var closed = false
        var corrupt = false
        init {
            val algorithm = AlgorithmIdentifier(if (rsa) PKCSObjectIdentifiers.sha256WithRSAEncryption else X9ObjectIdentifiers.ecdsa_with_SHA256)
            val name = X500Name("CN=Synthetic native MFi test only")
            val tbs = V3TBSCertificateGenerator().apply {
                setSerialNumber(ASN1Integer(BigInteger.ONE)); setSignature(algorithm)
                setIssuer(name); setSubject(name); setStartDate(Time(Date(0)))
                setEndDate(Time(Date(4102444800000L)))
                setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(pair.public.encoded))
            }.generateTBSCertificate()
            val signer = Signature.getInstance(if (rsa) "SHA256withRSA" else "SHA256withECDSA")
            signer.initSign(pair.private); signer.update(tbs.encoded)
            cert = DERSequence(arrayOf<ASN1Encodable>(tbs, algorithm, DERBitString(signer.sign()))).encoded
        }
        val input = object : InputStream() {
            override fun read(): Int = if (queue.isEmpty()) -1 else queue.removeFirst()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                val value = read(); if (value < 0) return -1
                b[off] = value.toByte(); return 1 // Deliberately fragment every read.
            }
        }
        val output = object : ByteArrayOutputStream() {
            override fun flush() {
                val frame = toByteArray(); reset(); frames.add(frame)
                if (frame[7] == 0x5b.toByte()) return
                val action = frame[8].toInt()
                val data = if (action == 1) cert else {
                    val payload = frame.copyOfRange(13, frame.size)
                    val signer = Signature.getInstance(if (rsa) "NONEwithRSA" else "NONEwithECDSA")
                    signer.initSign(pair.private)
                    if (rsa) signer.update(byteArrayOf(0x30,0x21,0x30,0x09,0x06,0x05,0x2b,0x0e,0x03,0x02,0x1a,0x05,0x00,0x04,0x14))
                    signer.update(payload)
                    var signature = signer.sign()
                    if (!rsa && rawEc) signature = LocalMfiAuthenticationClient.derToRaw(signature)
                    if (corrupt) signature[signature.lastIndex] = (signature.last().toInt() xor 1).toByte()
                    signature
                }
                val result = ByteArrayOutputStream()
                DataOutputStream(result).use {
                    it.write(byteArrayOf(0x5a,0xa5.toByte(),1)); it.writeInt(13 + data.size)
                    it.writeByte(0xaa); it.writeByte(action); it.writeInt(data.size); it.write(data)
                }
                result.toByteArray().forEach { queue.add(it.toInt() and 255) }
            }
        }
        fun open() = ArcfoxNativeMfi.open(ArcfoxMfiWire(input, output) { closed = true })
    }

    @Test fun ecChipSignsThroughExistingAuthenticatorContractWithFragmentedIpc() {
        for (raw in listOf(false, true)) {
            val chip = Chip(rawEc = raw)
            chip.open().use { client ->
                assertEquals(3, client.protocolMajor())
                assertArrayEquals(chip.cert, client.readCertificate())
                val returned = client.readCertificate(); returned.fill(0)
                assertArrayEquals(chip.cert, client.readCertificate())
                assertEquals(64, client.signChallenge(ByteArray(32) { 7 }).size)
                assertThrows(IllegalArgumentException::class.java) { client.signChallenge(ByteArray(20)) }
                assertThrows(IllegalArgumentException::class.java) { client.readCertificate(1) }
                assertArrayEquals(byteArrayOf(0x5a,0xa5.toByte(),1,0,0,0,10,0x5b,0,0xaa.toByte()), chip.frames.first())
                assertTrue(chip.frames.drop(1).all { it[7] == 0xaa.toByte() })
            }
            assertTrue(chip.closed)
        }
    }

    @Test fun rsaChipSelectsSha1AndVerifiesActualCertificateKey() {
        val chip = Chip(rsa = true)
        chip.open().use { client ->
            assertEquals(2, client.protocolMajor())
            assertEquals(128, client.signChallenge(ByteArray(20) { 9 }).size)
            assertThrows(IllegalArgumentException::class.java) { client.signChallenge(ByteArray(32)) }
        }
        assertTrue(chip.closed)
    }

    @Test fun unrelatedOrStaleSignatureFailsClosedInsteadOfBeingReturned() {
        for (rsa in listOf(false, true)) {
            val chip = Chip(rsa)
            val client = chip.open()
            chip.corrupt = true
            assertThrows(Exception::class.java) { client.signChallenge(ByteArray(if (rsa) 20 else 32)) }
            assertTrue(chip.closed)
            assertThrows(IllegalStateException::class.java) { client.signChallenge(ByteArray(if (rsa) 20 else 32)) }
        }
    }

    @Test fun malformedWrongActionTruncatedAndOversizedRepliesCloseTransport() {
        val replies = listOf(
            byteArrayOf(0x00),
            byteArrayOf(0x5a,0xa5.toByte(),1,0,1,0,0),
            byteArrayOf(0x5a,0xa5.toByte(),1,0,0,0,14,0xaa.toByte(),2,0,0,0,1,4),
            byteArrayOf(0x5a,0xa5.toByte(),1,0,0,0,14,0xaa.toByte(),1,0,0,0,5,4),
            byteArrayOf(0x5a,0xa5.toByte(),1,0,0,0,14,0xaa.toByte(),1),
        )
        for (bytes in replies) {
            var closed = false
            val wire = ArcfoxMfiWire(bytes.inputStream(), ByteArrayOutputStream()) { closed = true }
            assertThrows(Exception::class.java) { wire.request(1) }
            assertTrue(closed)
        }
        var closed = false
        val wire = ArcfoxMfiWire(object : InputStream() {
            override fun read(): Int = throw java.net.SocketTimeoutException("test timeout")
        }, ByteArrayOutputStream()) { closed = true }
        assertThrows(java.net.SocketTimeoutException::class.java) { wire.request(1) }
        assertTrue(closed)
    }
}
