# Script for building openssl for ios
#
# Usage: build-openssl src_dir dst_dir
#

set -e

if [[ -z "$1" ]] || [[ -z "$2" ]]; then
  echo "Usage: build-chromium-json src_dir dst_dir"
  exit 1
fi

SRC_DIR="$1"
DST_DIR="$2"

MITRO_API_SRC_DIR="${SRC_DIR}/mitro-api"

rm -rf ${MITRO_API_SRC_DIR}
mkdir -p ${MITRO_API_SRC_DIR}
cp -R ../cpp/ ${MITRO_API_SRC_DIR}

DEVELOPER_ROOT="/Applications/Xcode.app/Contents/Developer"
TOOLCHAIN_ROOT="${DEVELOPER_ROOT}/Toolchains/XcodeDefault.xctoolchain"
PLATFORM_ROOT="${DEVELOPER_ROOT}/Platforms/${PLATFORM}.platform/Developer"
SDK_ROOT="${PLATFORM_ROOT}/SDKs/${PLATFORM}${SDK_VERSION}.sdk"

CXX="${TOOLCHAIN_ROOT}/usr/bin/c++"
CXXFLAGS="-arch ${ARCH} -isysroot ${SDK_ROOT} -miphoneos-version-min=7.0 -I\"${DST_DIR}/include\" -I\"${MITRO_API_SRC_DIR}/thrift_json/thrift/lib/cpp/src\""
OBJCXX="${CXX}"
OBJCXXFLAGS="${CXXFLAGS}"
LDFLAGS="-arch ${ARCH} -dynamiclib"

echo "Building chromium for ${PLATFORM}${SDK_VERSION}"
pushd "${MITRO_API_SRC_DIR}"

./configure --host=arm-iphoneos --prefix=${DST_DIR} --enable-third_party=no CXX="${CXX}" CXXFLAGS="${CXXFLAGS}" OBJCXX="${OBJCXX}" OBJCXXFLAGS="${OBJCXXFLAGS}" LDFLAGS="${LDFLAGS}"

make clean
cd base
make
make install
popd
