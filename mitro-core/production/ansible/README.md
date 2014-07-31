# Ansible configurations for Mitro

[Ansible](http://docs.ansible.com/index.html) is a tool that automates configuring and installing software across computers. I've started to use it for some Mitro stuff, since it will make it easier to update stuff in the future.

## Setup
1. Change directory to `production/ansible`
1. Run `./setup.sh` to install Ansible locally


## Deploy Mitrocore

1. Deploy on primary: `./build/bin/ansible-playbook -i hosts mitrocore_deploy.yml --limit=primary --extra-vars="version=(version string)"`
2. After that is successful, deploy on the secondary: `./build/bin/ansible-playbook -i hosts mitrocore_deploy.yml --limit=secondary --extra-vars="version=(version string)"`


## SSL Certificates and Keys

Decrypts keys and copies the keys and certificates to both the primary and secondary. The keys are currently stored in the `unnamed` repository. This should be changed to use ansible-vault.

1. Run `./build/bin/ansible-playbook -i hosts -M modules ssl_keys_certs.yml`
2. Type the Lectorius key passphrase (three times due to a bug).
3. At the end it should say `PLAY RECAP` with nothing failed.
4. You will need to manually restart nginx if something changed (TODO: Automate this)


## Secondary

Configures nearly everything on the secondary, although it doesn't install the mitrocore server, doesn't take a snapshot, and doesn't configure postgres correctly. But its close:

`./build/bin/ansible-playbook -i hosts secondary.yml`


## Deploying a new primary with new data

This is only partly automated. Stuff you need to do manually:

1. Start an instance, put its IP or hostname in hosts as `[primary]`. If Debian: Install upstart on it (`sudo apt-get install upstart`); must be done manually
2. Download Oracle JDK 7 linux x64 .tar.gz from http://www.oracle.com/technetwork/java/javase/downloads/index.html into a sub-directory called `third_party`.
3. Edit `primary.yml` to change `jdk_version` to the downloaded version
4. Generate a self-signed SSL key and certificate. Copy `mitro_co.pem` and `mitro_co.crt` to `/etc/nginx`
  ```
openssl req -nodes -newkey rsa:2048 -keyout mitro_co.pem -out mitro_co.csr
openssl x509 -req -days 1000 -in mitro_co.csr -signkey mitro_co.pem -out mitro_co.crt
```
5. Generate a new Keyczar "signing" key (verifies that email and 2FA redirects really come from the server); copy it
  ```
mkdir -p mitrocore_secrets/sign_keyczar
java -cp ../../build/mitrocore.jar org.keyczar.KeyczarTool create --location=primary/mitrocore/mitrocore_secrets/sign_keyczar --purpose=sign
java -cp ../../build/mitrocore.jar org.keyczar.KeyczarTool addkey --location=primary/mitrocore/mitrocore_secrets/sign_keyczar --status=primary
rsync -r mitrocore_secrets primary:mitrocore
```
6. Configure the base software: `./build/bin/ansible-playbook -i hosts primary.yml`
7. Install and run the mitro-core server: `./build/bin/ansible-playbook -i hosts-benchmark mitrocore_deploy.yml --limit=primary --extra-vars="version=20140621"`
8. Remove mitroweb; start nginx: `sudo rm /etc/nginx/sites-enabled/mitroweb; sudo service nginx start`
9. Create the database role `sudo -u postgres psql`:
   ```

create user ubuntu;
create user mitrocore;
create database mitro owner=mitrocore;
\connect mitro
ALTER DEFAULT PRIVILEGES FOR USER mitrocore GRANT SELECT ON TABLES TO ubuntu;
ALTER DEFAULT PRIVILEGES FOR USER mitrocore GRANT SELECT ON SEQUENCES TO ubuntu;
```


## Deploy a new primary with production data

Run the ansible scripts in the following order:

1. `mitrocore_deploy.yml`; it will fail to start due to lack of Java
2. `ssl_keys_certs.yml`; it should work
3. `primary.yml`; it should also work
