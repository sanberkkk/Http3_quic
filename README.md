# INF 334 — QUIC / HTTP/3

## Derleme

```bash
mvn -pl part3,part1,bonus package -DskipTests
```

## Kısım I

**Terminal 1 — sunucu**
```bash
./part1/run-server.sh
```

**Terminal 2 — istemci**
```bash
./part1/run-client.sh
```

Log: `quic_project_logs.txt`

## Bonus

**JNI (bir kez)**
```bash
chmod +x bonus/setup-jni.sh
./bonus/setup-jni.sh
export QUICHE_JNI_LIB=/tmp/quiche4j-native/release
```

**Terminal 1 — sunucu**
```bash
docker stop quiche-server 2>/dev/null
export QUICHE_JNI_LIB=/tmp/quiche4j-native/release
./bonus/run-server.sh 4433
```

**Terminal 2 — istemci**
```bash
export QUICHE_JNI_LIB=/tmp/quiche4j-native/release
./bonus/run-client.sh https://127.0.0.1:4433/
```
