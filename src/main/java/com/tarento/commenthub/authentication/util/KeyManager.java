package com.tarento.commenthub.authentication.util;

import com.tarento.commenthub.authentication.model.KeyData;
import com.tarento.commenthub.constant.Constants;
import com.tarento.commenthub.transactional.utils.PropertiesCache;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class KeyManager {

  private static final Logger logger = LoggerFactory.getLogger(KeyManager.class.getName());
  private final PropertiesCache propertiesCache;

  private static final Map<String, KeyData> keyMap = new HashMap<>();

  public KeyManager(PropertiesCache propertiesCache) {
    this.propertiesCache = propertiesCache;
  }

  @PostConstruct
  public void init() {
    // Read the content of the file and load it as a PublicKey
    String basePath = propertiesCache.getProperty(Constants.ACCESS_TOKEN_PUBLICKEY_BASEPATH);
    try (Stream<Path> walk = Files.walk(Paths.get(basePath))) {
      List<String> result =
          walk.filter(Files::isRegularFile).map(Path::toString).toList();
      result.forEach(file -> {
        try {
          Path path = Paths.get(file);
          List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
          String content = String.join("", lines);
          KeyData keyData = new KeyData(path.getFileName().toString(), loadPublicKey(content));
          // Store the KeyData object in the keyMap
          keyMap.put(path.getFileName().toString(), keyData);
        } catch (Exception e) {
          logger.error("KeyManager:init: exception in reading public keys ", e);
        }
      });
    } catch (Exception e) {
      logger.error("KeyManager:init: exception in loading publickeys ", e);
    }
  }

  public KeyData getPublicKey(String keyId) {
    return keyMap.get(keyId);
  }


  /**
   * Loads a public key from a string representation.
   *
   * @param key The string representation of the public key
   * @return The loaded public key
   * @throws NoSuchAlgorithmException If the RSA algorithm is not available
   * @throws InvalidKeySpecException If the key spec is invalid
   */
  public static PublicKey loadPublicKey(String key) throws NoSuchAlgorithmException, InvalidKeySpecException {
    String publicKey = new String(key.getBytes(), StandardCharsets.UTF_8);
    // Remove header and footer from the key string
    publicKey = publicKey.replaceAll("(-{1,10}BEGIN PUBLIC KEY-{1,10})", "");
    publicKey = publicKey.replaceAll("(-{1,10}END PUBLIC KEY-{1,10})", "");
    publicKey = publicKey.replaceAll("[\\r\\n]+", "");
    // Decode the key string from Base64
    byte[] keyBytes = Base64Util.decode(publicKey.getBytes(StandardCharsets.UTF_8), Base64Util.DEFAULT);
    // Convert the key bytes to a PublicKey object
    X509EncodedKeySpec x509publicKey = new X509EncodedKeySpec(keyBytes);
    KeyFactory kf = KeyFactory.getInstance("RSA");
    return kf.generatePublic(x509publicKey);
  }

}
