#ifndef MITRO_KEYCZAR_SESSION__
#define MITRO_KECZAR_SESSION__

#include <string>

namespace keyczar {
class AESKey;
class Crypter;
class Keyczar;
}  // namespace keyczar

namespace mitro {

// Limited implementation of Java's Util.lenPrefixPack;
std::string LenPrefixPack(const std::string* const strings[], size_t num_strings);
bool PairPrefixUnpack(const std::string& data, std::string* v1, std::string* v2);

std::string PackAESKey(const keyczar::AESKey& aesKey);
keyczar::AESKey* UnpackAESKey(const std::string& data);

bool EncryptSession(const keyczar::Keyczar& crypter, const std::string& data, std::string* output);
bool DecryptSession(const keyczar::Keyczar& crypter, const std::string& data, std::string* output);

}  // namespace mitro
#endif
