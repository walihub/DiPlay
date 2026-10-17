.class public final Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;
.super Ljava/lang/Object;

.field private static fetchKey:Ljava/lang/String;
.field private static fetchAt:J
.field private static imageKey:Ljava/lang/String;
.field private static imageBytes:[B
.field private static imageAt:J
.field private static titleDevice:Ljava/lang/String;
.field private static titleBytes:[B

.method public static reset()V
    .locals 1
    const/4 v0, 0x0
    sput-object v0, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->fetchKey:Ljava/lang/String;
    sput-object v0, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->imageKey:Ljava/lang/String;
    sput-object v0, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->imageBytes:[B
    sput-object v0, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->titleDevice:Ljava/lang/String;
    sput-object v0, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->titleBytes:[B
    return-void
.end method

.method public static onTitle([B[B)V
    .locals 3
    if-eqz p0, :done
    if-eqz p1, :done
    invoke-static {p0}, Lcom/android/bluetooth/Utils;->getAddressStringFromByte([B)Ljava/lang/String;
    move-result-object v0
    sget-object v1, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->titleDevice:Ljava/lang/String;
    if-eqz v1, :changed
    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v1
    if-eqz v1, :changed
    sget-object v1, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->titleBytes:[B
    invoke-static {p1, v1}, Ljava/util/Arrays;->equals([B[B)Z
    move-result v1
    if-nez v1, :done
    :changed
    invoke-static {}, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->reset()V
    sput-object v0, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->titleDevice:Ljava/lang/String;
    invoke-virtual {p1}, [B->clone()Ljava/lang/Object;
    move-result-object v1
    check-cast v1, [B
    sput-object v1, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->titleBytes:[B
    :done
    return-void
.end method

.method private static key(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
    .locals 2
    new-instance v0, Ljava/lang/StringBuilder;
    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V
    invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    const-string v1, "|"
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v0
    return-object v0
.end method

.method public static shouldFetch([BLjava/lang/String;)Z
    .locals 7
    if-eqz p0, :allow
    if-eqz p1, :allow
    invoke-static {p0}, Lcom/android/bluetooth/Utils;->getAddressStringFromByte([B)Ljava/lang/String;
    move-result-object v0
    invoke-static {v0, p1}, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->key(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v2
    invoke-static {}, Landroid/os/SystemClock;->elapsedRealtime()J
    move-result-wide v3
    sget-object v0, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->fetchKey:Ljava/lang/String;
    if-eqz v0, :save
    invoke-virtual {v2, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :save
    sget-wide v5, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->fetchAt:J
    sub-long/2addr v3, v5
    const-wide/16 v5, 0x7530
    cmp-long v0, v3, v5
    if-gez v0, :refresh
    const/4 v0, 0x0
    return v0
    :refresh
    invoke-static {}, Landroid/os/SystemClock;->elapsedRealtime()J
    move-result-wide v3
    :save
    sput-object v2, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->fetchKey:Ljava/lang/String;
    sput-wide v3, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->fetchAt:J
    :allow
    const/4 v0, 0x1
    return v0
.end method

.method public static shouldBroadcast(Landroid/bluetooth/BluetoothDevice;Ljava/lang/String;[B)Z
    .locals 7
    if-eqz p2, :reject
    array-length v0, p2
    if-lez v0, :reject
    if-eqz p0, :allow
    invoke-virtual {p0}, Landroid/bluetooth/BluetoothDevice;->getAddress()Ljava/lang/String;
    move-result-object v0
    invoke-static {v0, p1}, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->key(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v2
    invoke-static {}, Landroid/os/SystemClock;->elapsedRealtime()J
    move-result-wide v3
    sget-object v0, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->imageKey:Ljava/lang/String;
    if-eqz v0, :save
    invoke-virtual {v2, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :save
    sget-object v0, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->imageBytes:[B
    invoke-static {v0, p2}, Ljava/util/Arrays;->equals([B[B)Z
    move-result v0
    if-eqz v0, :save
    sget-wide v5, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->imageAt:J
    sub-long/2addr v3, v5
    const-wide/32 v5, 0xea60
    cmp-long v0, v3, v5
    if-gez v0, :refresh
    :reject
    const/4 v0, 0x0
    return v0
    :refresh
    invoke-static {}, Landroid/os/SystemClock;->elapsedRealtime()J
    move-result-wide v3
    :save
    sput-object v2, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->imageKey:Ljava/lang/String;
    invoke-virtual {p2}, [B->clone()Ljava/lang/Object;
    move-result-object v0
    check-cast v0, [B
    sput-object v0, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->imageBytes:[B
    sput-wide v3, Lcom/android/bluetooth/nfservice/ArcfoxCoverGate;->imageAt:J
    :allow
    const/4 v0, 0x1
    return v0
.end method
