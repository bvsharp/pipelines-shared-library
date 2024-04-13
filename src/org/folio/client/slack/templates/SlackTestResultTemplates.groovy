package org.folio.client.slack.templates

class SlackTestResultTemplates {

  private SlackTestResultTemplates(){}

  static final CYPRESS_TEXT = 'Build name: $TEST_TXT_BUILD_NAME. Passed tests: $TEST_TXT_PASS_CNT, Broken tests: $TEST_TXT_BRK_CNT, Failed tests: $TEST_TXT_FAIL_CNT, Pass rate: $TEST_TXT_PASS_RATE%'
  static final CYPRESS_TITLE = 'Cypress tests results :arrow_forward:'

  static final KARATE_TEXT = 'Passed tests: $TEST_TXT_PASS_CNT, Failed tests: $TEST_TXT_FAIL_CNT, Pass rate: $TEST_TXT_PASS_RATE%'
  static final KARATE_TITLE = 'Karate tests results :martial_arts_uniform:'

  static final ACTION_TEXT = '*Check out the tests report* :bar_chart: '

  static final REPORT_PORTAL_ACTION_TEXT = '*ReportPortal results* :bar_chart: '
}
