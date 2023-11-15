package org.folio.utilities

import java.security.SecureRandom

class Toolkit {

  static String generateRandomPassword(int length, boolean includeUpper = true, boolean includeLower = true, boolean includeNumbers = false, boolean includeSpecial = false) {
    String upperCaseChars = includeUpper ? 'ABCDEFGHIJKLMNOPQRSTUVWXYZ' : ''
    String lowerCaseChars = includeLower ? upperCaseChars.toLowerCase() : ''
    String numberChars = includeNumbers ? '0123456789' : ''
    String specialChars = includeSpecial ? '!@#$%^&*()-_+=' : ''
    String allChars = upperCaseChars + lowerCaseChars + numberChars + specialChars

    if (allChars.isEmpty()) {
      throw new IllegalArgumentException('At least one character type must be included')
    }

    SecureRandom random = new SecureRandom()
    StringBuilder password = new StringBuilder(length)

    // Add at least one character of each type if included
    if (includeUpper) password.append(upperCaseChars.charAt(random.nextInt(upperCaseChars.length())))
    if (includeLower) password.append(lowerCaseChars.charAt(random.nextInt(lowerCaseChars.length())))
    if (includeNumbers) password.append(numberChars.charAt(random.nextInt(numberChars.length())))
    if (includeSpecial) password.append(specialChars.charAt(random.nextInt(specialChars.length())))

    // Fill the remaining slots with random characters from the allowed types
    for (int i = password.length(); i < length; i++) {
      password.append(allChars.charAt(random.nextInt(allChars.length())))
    }

    // Shuffle the characters for added randomness
    List<Character> passwordChars = password.toString().toList()
    Collections.shuffle(passwordChars)
    return passwordChars.join("")
  }
}
