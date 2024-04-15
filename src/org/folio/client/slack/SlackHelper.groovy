package org.folio.client.slack

import org.folio.client.slack.templates.SlackMessageTemplates

final class SlackHelper {

  private SlackHelper() {}

  static String renderAction(String url, String text){
    return fillTemplate(
            SlackMessageTemplates.getActionParams(url, text)
            , SlackMessageTemplates.ACTION)
  }

  static String renderActions(List<String> actions){
    return "[${actions.join(', ')}]"
  }

  static String renderField(String title, String value, boolean isShort){
    return fillTemplate(
            SlackMessageTemplates.getFieldParams(title, value, isShort)
            , SlackMessageTemplates.FIELD)
  }

  static String renderFields(List<String> fields){
    return "[${fields.join(', ')}]"
  }

  static String renderSection(String title, String message, String color, List<String> actions, List<String> fields){
    return fillTemplate(
            SlackMessageTemplates.getSectionParams(title, message, color, renderActions(actions), renderFields(fields))
            , SlackMessageTemplates.SECTION)
  }

  static String renderMessage(List<String> sections){
    return "[${sections.join(', ')}]"
  }

  static String fillTemplate(Map<String, String> params, String template){
    params.each { key, value ->
      template = template.replace("\${${key}}", value)
    }
    return template
  }
}
