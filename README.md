## FIXEngine Remote gRPC API

### Contents

proto/*.proto &ndash; defines FIXEngine Remote gRPC service

java-sample/FIXEngineAppGrpc.java &ndash; Java sample

### Instructions how to run Java sample

1. Change current folder to _java-sample_
`cd java-sample`

2. Get _cert.pem_ SSL certificate file from InfoReach and put it to the current folder

3. Download dependencies and compile the sample
`mvn compile`

4. Run the sample
`mvn exec:exec`
