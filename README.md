# INF 334 — QUIC / HTTP/3

## Derleme

```bash
mvn -pl part3,part1,bonus package -DskipTests
```

## Kısım I

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
