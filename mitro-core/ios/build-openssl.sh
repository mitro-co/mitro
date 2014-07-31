# Script for building openssl for ios
#
# Usage: build-openssl src_dir dst_dir
#

set -e

if [[ -z "$1" ]] || [[ -z "$2" ]]; then
  echo "Usage: build-openssl src_dir dst_dir"
  exit 1
fi

SRC_DIR="$1"
DST_DIR="$2"

OPENSSL_TAR_GZ="openssl-1.0.1e.tar.gz"
OPENSSL_SHA1="3f1b1223c9e8189bfe4e186d86449775bd903460"
OPENSSL_URL="http://www.openssl.org/source/${OPENSSL_TAR_GZ}"
OPENSSL_SRC_DIR="${SRC_DIR}/${OPENSSL_TAR_GZ%.tar.gz}"

../tools/download_and_extract.sh ${OPENSSL_URL} ${OPENSSL_SHA1} ${SRC_DIR}

DEVELOPER_ROOT="/Applications/Xcode.app/Contents/Developer"
TOOLCHAIN_ROOT="${DEVELOPER_ROOT}/Toolchains/XcodeDefault.xctoolchain"
PLATFORM_ROOT="${DEVELOPER_ROOT}/Platforms/${PLATFORM}.platform/Developer"
SDK_ROOT="${PLATFORM_ROOT}/SDKs/${PLATFORM}${SDK_VERSION}.sdk"

CC="${TOOLCHAIN_ROOT}/usr/bin/cc"
CFLAGS="-arch ${ARCH} -isysroot ${SDK_ROOT} -D_DARWIN_C_SOURCE -UOPENSLL_BN_ASM_PART_WORDS -miphoneos-version-min=7.0 -O3"
LDFLAGS="-arch ${ARCH} -dynamiclib"

echo "Building openssl for ${PLATFORM}${SDK_VERSION}"
pushd "${OPENSSL_SRC_DIR}"
./Configure BSD-generic32 no-shared no-asm no-krb5 no-gost zlib --openssldir=${DST_DIR}
make clean
make CC="${CC}" CFLAG="${CFLAGS}" SHARED_LDFLAGS="${LDFLAGS}"
make install
popd
