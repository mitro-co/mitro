#!/bin/bash

BuildStep() {
  pushd src
  sh ./tools/swtoolkit/hammer.sh --mode=opt-nacl --compat --verbose
  popd
}

InstallStep() {
  pushd src
  sh ./tools/swtoolkit/hammer.sh --mode=opt-nacl --compat --verbose prefix=${NACLPORTS_PREFIX} install
  popd
}
