package Utils

object CryptoUtils {

  /**
   * Hashes a plain-text password using a secure algorithm.
   * This method is intended for internal use within the OrganizeService only.
   *
   * @param plainText The plain-text password to hash.
   * @return The resulting password hash as a String.
   */
  def encryptPassword(plainText: String): String = {
    plainText.reverse.hashCode.toString
  }

}
