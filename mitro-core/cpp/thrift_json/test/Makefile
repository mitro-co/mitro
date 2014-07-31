.PHONY: all clean
all: thrift_json_test

gen-cpp/test_types.cpp: test.thrift
	thrift --gen cpp $^

thrift_json_test: gen-cpp/test_types.cpp thrift_json_test.cc
	g++ -std=gnu++11 -I../.. -I../../third_party/include -I gen-cpp -o $@ $^ -L../../third_party/lib -lthrift ../../third_party/lib/libgtest.a ../../third_party/lib/libgtest_main.a

clean:
	rm -rf gen-cpp thrift_json_test
