#!/bin/bash
# Sets up local ansible install

set -e


if [ "$(uname)" == "Darwin" ]; then
  # these variables are necessary to fix a clang breakage.
  # https://stackoverflow.com/questions/22313407/clang-error-unknown-argument-mno-fused-madd-python-package-installation-fa
  export CFLAGS=-Qunused-arguments
  export CPPFLAGS=-Qunused-arguments
fi

virtualenv build
./build/bin/pip install ansible

echo "Installed! Example command line:"
echo " ./build/bin/ansible-playbook -i hosts -M modules secondary.yml"
