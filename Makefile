build:
	./gradlew \
	cipher:clean core:clean social:clean \
	cipher:assemble core:assemble social:assemble \
	-x check --refresh-dependencies

pods:
	./gradlew \
	all:assembleKnostrXCFramework \
	all:podPublishXCFramework \
	-x check --refresh-dependencies

version:
	 ./gradlew version --no-daemon --console=plain -q

.PHONY: build pods version
