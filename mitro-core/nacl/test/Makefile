VALID_TOOLCHAINS := pnacl

include $(NACL_SDK_ROOT)/tools/common.mk

TARGET = thrift_nacl_test
LIBS = thrift ppapi_simple nacl_io ppapi_cpp ppapi gtest

THIRD_PARTY_PREFIX = $(TC_PATH)/$(OSNAME)_$(TOOLCHAIN)
CXXFLAGS = -Igen-cpp -I$(THIRD_PARTY_PREFIX)/usr/include

SOURCES = \
gen-cpp/test_types.cpp \
thrift_nacl_test.cc

THRIFT = ../../cpp/third_party/bin/thrift

gen-cpp/test_types.cpp: test.thrift
	$(THRIFT) --gen cpp $^

$(foreach src,$(SOURCES),$(eval $(call COMPILE_RULE,$(src),$(CXXFLAGS))))

$(eval $(call LINK_RULE,$(TARGET),$(SOURCES),$(LIBS),$(DEPS)))

$(eval $(call NMF_RULE,$(TARGET),))
