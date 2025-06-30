package Utils

object CryptoUtils {

  /**
   * Hashes a plain-text password using a placeholder algorithm.
   * This method is intended for internal use within the OrganizeService only.
   *
   * @param plainText The plain-text password to hash.
   * @return The resulting password hash as a String.
   */
  def encryptPassword(plainText: String): String = {
    // 这是一个占位符实现。
    // 在生产环境中，强烈建议使用 BCrypt 这样的强哈希库。
    plainText.reverse.hashCode.toString
  }

  /**
   * Verifies a plain-text password against a stored hash using the placeholder algorithm.
   *
   * @param plainText The plain-text password to verify.
   * @param storedHash The stored hash to compare against.
   * @return true if the password matches the hash, false otherwise.
   */
  def verifyPassword(plainText: String, storedHash: String): Boolean = {
    // 这个验证逻辑必须与 encryptPassword 的逻辑相对应。
    // 即：用同样的方式加密明文，然后与存储的哈希进行比较。
    //
    // 注意: 当使用 BCrypt 这样的真实库时，这里应该调用库提供的专用验证函数，
    // 例如：BCrypt.verifyer().verify(plainText.toCharArray, storedHash).verified
    encryptPassword(plainText) == storedHash
  }
}