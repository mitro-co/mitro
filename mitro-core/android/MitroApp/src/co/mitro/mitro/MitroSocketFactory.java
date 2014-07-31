/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package co.mitro.mitro;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

// The sole purpose of this class is for overriding the set of enabled cipher
// suites for SSL.  It is necessary to enable 256-bit SSL ciphers that were
// disabled on android 2.3.  Without these ciphers enabled, we are unable to
// open an SSL connection to mitro.co.
//
// See https://code.google.com/p/android/issues/detail?id=18688
public class MitroSocketFactory extends SSLSocketFactory {

    private SSLSocketFactory delegate;

    public MitroSocketFactory(SSLSocketFactory delegate) {
      this.delegate = delegate;
    }

    public String[] getDefaultCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    public String[] getSupportedCipherSuites() {
      return delegate.getSupportedCipherSuites();
    }

    private Socket enableAllCipherSuites(Socket socket) {
      SSLSocket sslSocket = (SSLSocket) socket;
      sslSocket.setEnabledCipherSuites(getSupportedCipherSuites());
      return sslSocket;
    }

    public Socket createSocket() throws IOException {
        return enableAllCipherSuites(delegate.createSocket());
    }

    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        return enableAllCipherSuites(delegate.createSocket(host, port));
    }

    public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
            throws IOException, UnknownHostException {
        return enableAllCipherSuites(delegate.createSocket(host, port, localHost, localPort));
    }

    public Socket createSocket(InetAddress host, int port) throws IOException {
        return enableAllCipherSuites(delegate.createSocket(host, port));
    }

    public Socket createSocket(InetAddress address,
                               int port,
                               InetAddress localAddress,
                               int localPort)
            throws IOException {
        return enableAllCipherSuites(delegate.createSocket(address, port, localAddress, localPort));
    }

    public Socket createSocket(Socket s, String host, int port, boolean autoClose)
            throws IOException {
        return enableAllCipherSuites(delegate.createSocket(s, host, port, autoClose));
    }
}
