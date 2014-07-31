noinst_LIBRARIES = lib/libgtest.a lib/libkeyczar.a lib/libthrift.a

lib/libgtest.a:
	./build-lib.sh build-gtest.sh

lib/libkeyczar.a:
	./build-lib.sh build-keyczar.sh

include/boost:
	./build-lib.sh build-boost.sh

lib/libthrift.a: include/boost
	./build-lib.sh build-thrift.sh
