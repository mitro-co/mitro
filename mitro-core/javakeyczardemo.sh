#!/bin/sh
# Demonstrates using Java Keyczar to generate keys and output

set -e

MESSAGE="hello world message"

JARS="""
    keyczar-0.71f-040513.jar
    gson-2.2.4.jar
    log4j-1.2.17.jar
"""
CLASSPATH_BASE="java/server/lib"

CLASSPATH=""
for jar in $JARS; do
    CLASSPATH="$CLASSPATH:$CLASSPATH_BASE/$jar"
done
# echo $CLASSPATH

mkdir -p test/privatekey
java -cp $CLASSPATH org.keyczar.KeyczarTool create --location=test/privatekey --asymmetric=rsa --purpose=crypt
echo "generating encryption key ..."
time java -cp $CLASSPATH org.keyczar.KeyczarTool addkey --location=test/privatekey --status=primary
mkdir -p test/publickey
java -cp $CLASSPATH org.keyczar.KeyczarTool pubkey --location=test/privatekey --destination=test/publickey

mkdir -p test/privatekey_sign
java -cp $CLASSPATH org.keyczar.KeyczarTool create --location=test/privatekey_sign --asymmetric=rsa --purpose=sign
echo "generating signing key ..."
time java -cp $CLASSPATH org.keyczar.KeyczarTool addkey --location=test/privatekey_sign --status=primary
mkdir -p test/publickey_sign
java -cp $CLASSPATH org.keyczar.KeyczarTool pubkey --location=test/privatekey_sign --destination=test/publickey_sign

mkdir -p test/symmetric
java -cp $CLASSPATH org.keyczar.KeyczarTool create --location=test/symmetric --purpose=crypt
java -cp $CLASSPATH org.keyczar.KeyczarTool addkey --location=test/symmetric --status=primary

java -cp $CLASSPATH org.keyczar.KeyczarTool usekey "$MESSAGE" --location=test/privatekey --destination=test/privatekey_encrypted
java -cp $CLASSPATH org.keyczar.KeyczarTool usekey "$MESSAGE" --location=test/privatekey_sign --destination=test/privatekey_sign_signed
java -cp $CLASSPATH org.keyczar.KeyczarTool usekey "$MESSAGE" --location=test/symmetric --destination=test/symmetric_encrypted

mkdir -p test/no_keys
java -cp $CLASSPATH org.keyczar.KeyczarTool create --location=test/no_keys --asymmetric=rsa --purpose=crypt

mkdir -p test/no_primary
java -cp $CLASSPATH org.keyczar.KeyczarTool create --location=test/no_primary --asymmetric=rsa --purpose=crypt
java -cp $CLASSPATH org.keyczar.KeyczarTool addkey --location=test/no_primary

for key in privatekey publickey privatekey_sign publickey_sign symmetric no_keys no_primary; do
    java -cp $CLASSPATH:bin co.mitro.keyczar.JsonWriter test/$key > test/$key.json
done
