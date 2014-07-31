# Script for building Mitro iOS libraries.
#

set -e

ROOT_DIR=$( cd "$( dirname "$0" )" && pwd )
ABSOLUTE_BUILD="${ROOT_DIR}/build"
SRC_DIR="${ABSOLUTE_BUILD}/src"
SCRIPTS_DIR="${ROOT_DIR}/../cpp/third_party"

mkdir -p "${SRC_DIR}"

export TOOLS_DIR=../tools
export PATCH_DIR="${SCRIPTS_DIR}"

PLATFORMS="iPhoneSimulator iPhoneOS"

for PLATFORM in ${PLATFORMS}; do
  export PLATFORM=${PLATFORM}
  export SDK_VERSION="7.0"

  if [ "${PLATFORM}" == "iPhoneSimulator" ]; then
    ARCHS="i386"
  else
    ARCHS="armv7 armv7s"
  fi

  for ARCH in ${ARCHS}; do
    export ARCH=${ARCH}
    DST_DIR="${ABSOLUTE_BUILD}/${PLATFORM}${SDK_VERSION}/${ARCH}"

    "${SCRIPTS_DIR}/build-gtest.sh" "${SRC_DIR}" "${DST_DIR}"
    "${ROOT_DIR}/build-openssl.sh" "${SRC_DIR}" "${DST_DIR}"
    "${SCRIPTS_DIR}/build-keyczar.sh" "${SRC_DIR}" "${DST_DIR}"
    "${ROOT_DIR}/build-chromium-json.sh" "${SRC_DIR}" "${DST_DIR}"
    "${SCRIPTS_DIR}/build-boost.sh" "${SRC_DIR}" "${DST_DIR}"
    "${SCRIPTS_DIR}/build-thrift.sh" "${SRC_DIR}" "${DST_DIR}"
    "${ROOT_DIR}/build-mitro-api.sh" "${SRC_DIR}" "${DST_DIR}"
  done

  echo  "Creating mutli-arch libraries"
  LIBS="libcrypto.a libkeyczar.a libthrift.a libchromiumbase.a libchromiumjson.a libhttpclient.a libkeyczarjson.a"

  OUTPUT_DIR="${ABSOLUTE_BUILD}/${PLATFORM}${SDK_VERSION}"
  OUTPUT_INCLUDE_DIR="${OUTPUT_DIR}/include"
  OUTPUT_LIB_DIR="${OUTPUT_DIR}/lib"
  mkdir -p "${OUTPUT_LIB_DIR}"

  for LIB in ${LIBS}; do
    INPUT_FILES=

    for ARCH in ${ARCHS}; do
      DST_DIR="${ABSOLUTE_BUILD}/${PLATFORM}${SDK_VERSION}/${ARCH}/lib"
      INPUT_FILES+="${DST_DIR}/${LIB} "
    done

    OUTPUT_FILE="${OUTPUT_LIB_DIR}/${LIB}"
    echo ${OUTPUT_FILE}

    lipo ${INPUT_FILES} -create -output ${OUTPUT_FILE}
  done

  echo "Installing include headers"
  rm -rf ${OUTPUT_INCLUDE_DIR}
  cp -r "${OUTPUT_DIR}/${ARCHS%% *}/include" "${OUTPUT_INCLUDE_DIR}"
done
