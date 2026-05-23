# INF 334 — QUIC / HTTP/3

## Derleme

```bash
mvn -pl part3,part1,bonus package -DskipTests
```

## Kısım I

**Senaryo 1: Rust ve quiche bilgisayarınızda yüklü ise**
```bash
./target/debug/examples/server \
  --listen 0.0.0.0:4433 \
  --root . \
  --cert bonus/src/main/resources/cert.crt \
  --key bonus/src/main/resources/cert.key \
  --no-retry
```

**Senaryo 2: Docker yüklü ise**

**1. Adım**
Bize Docker imaj erişimi için ulaşmak (moodle'a yüklemek için biraz uzun bir dosya)

**2. Adım**
Terminaller:

**Terminal 1**
```bash
./part1/run-server.sh
```

**Terminal 2**
```bash
./part1/run-client.sh
```

## Bonus

**JNI (bir kez)**
```bash
chmod +x bonus/setup-jni.sh
./bonus/setup-jni.sh
export QUICHE_JNI_LIB=/tmp/quiche4j-native/release
```

**Terminal 1**
```bash
export QUICHE_JNI_LIB=/tmp/quiche4j-native/release
./bonus/run-server.sh 4433
```

**Terminal 2**
```bash
export QUICHE_JNI_LIB=/tmp/quiche4j-native/release
./bonus/run-client.sh https://127.0.0.1:4433/
```
