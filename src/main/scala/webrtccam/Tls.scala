package webrtccam

import java.security.{KeyStore, Security}
import javax.net.ssl.{KeyManagerFactory, SSLContext}

/*
  565  openssl req -x509 -newkey rsa:4096 -sha256 -days 3650   -nodes -keyout example.com.key -out example.com.crt -subj "/CN=example.com"   -addext "subjectAltName=DNS:example.com,DNS:*.example.com,IP:192.168.50.143
  570  openssl pkcs12 -export -in example.com.crt -inkey example.com.key -out abc.p12
  571  keytool -importkeystore -srckeystore abc.p12         -srcstoretype PKCS12         -destkeystore abc.jks         -deststoretype JKS
 */

object Tls {
  def context: SSLContext = {
    val keystorePassword = "changeit"
    val keyManagerPass = "changeit"

    val ksStream = this.getClass.getResourceAsStream("/ssl/abc.jks")
    val ks = KeyStore.getInstance("JKS")
    ks.load(ksStream, keystorePassword.toCharArray)
    ksStream.close()

    val kmf = KeyManagerFactory.getInstance(
      Option(Security.getProperty("ssl.KeyManagerFactory.algorithm"))
        .getOrElse(KeyManagerFactory.getDefaultAlgorithm)
    )

    kmf.init(ks, keyManagerPass.toCharArray)

    val context = SSLContext.getInstance("TLS")
    context.init(kmf.getKeyManagers, null, null)

    context
  }
}
