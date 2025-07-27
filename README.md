# [Backend part see here](https://github.com/gubnota/otp_sync_backend)

## How to build

![video](https://github.com/user-attachments/assets/b106ab6e-db6a-45d1-a1f2-f367561ad184)

1. generate secret: `openssl rand -hex 32`
2. create ~~`app-settings.properties`~~ `.env` file in the root of the project
   (see `example.env` for reference)
3. replace self-signed certificate in `app/src/main/res/raw/cert.crt`
4. build:

```bash
./gradlew clean
./gradlew build
./gradlew assembleDebug
./gradlew assembleRelease
```

TODO:

- [x] 1. make sure READ_CALL_LOG is granted
- [x] 2. add input fields as secret, URL to POST data, id of the user(s) to notify
- [x] 3. add version code and name
- [x] 4. add default secret to app-settings.properties
- [x] 5. notify about calls
