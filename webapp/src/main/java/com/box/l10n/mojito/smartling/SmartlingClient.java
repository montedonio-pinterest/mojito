package com.box.l10n.mojito.smartling;

import com.box.l10n.mojito.iterators.PageFetcherOffsetAndLimitSplitIterator;
import com.box.l10n.mojito.json.ObjectMapper;
import com.box.l10n.mojito.smartling.request.Bindings;
import com.box.l10n.mojito.smartling.response.*;
import com.google.common.base.Charsets;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import reactor.util.retry.RetryBackoffSpec;

public class SmartlingClient {

  public enum RetrievalType {
    PENDING("pending"),
    PUBLISHED("published"),
    PSEUDO("pseudo"),
    CONTEXT_MATCHING_INSTRUMENTED("contextmatchinginstrumented");

    private String value;

    RetrievalType(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  /** logger */
  static Logger logger = LoggerFactory.getLogger(SmartlingClient.class);

  static final String API_SOURCE_STRINGS =
      "strings-api/v2/projects/{projectId}/source-strings?fileUri={fileUri}&offset={offset}&offset={limit}";
  static final String API_FILES_LIST = "files-api/v2/projects/{projectId}/files/list";
  static final String API_FILES_UPLOAD = "files-api/v2/projects/{projectId}/file";
  static final String API_FILES_UPLOAD_LOCALIZED =
      "files-api/v2/projects/{projectId}/locales/{localeId}/file/import";
  static final String API_FILES_DOWNLOAD =
      "files-api/v2/projects/{projectId}/locales/{locale_id}/file?fileUri={fileUri}&includeOriginalStrings={includeOriginalStrings}&retrievalType={retrievalType}";
  static final String API_FILES_DELETE = "files-api/v2/projects/{projectId}/file/delete";
  static final String API_CONTEXTS = "context-api/v2/projects/{projectId}/contexts";
  static final String API_CONTEXTS_DETAILS =
      "context-api/v2/projects/{projectId}/contexts/{contextId}";
  static final String API_BINDINGS = "context-api/v2/projects/{projectId}/bindings";
  static final String API_GLOSSARY_DETAILS =
      "glossary-api/v2/accounts/{accountId}/glossaries/{glossaryId}";
  static final String API_GLOSSARY_SOURCE_TBX_DOWNLOAD =
      "glossary-api/v2/accounts/{accountId}/glossaries/{glossaryId}/download?format=tbx&localeIds={locale}";
  static final String API_GLOSSARY_TRANSLATED_TBX_DOWNLOAD =
      "glossary-api/v2/accounts/{accountId}/glossaries/{glossaryId}/download?format=tbx&localeIds={locale},{sourceLocale}";
  static final String API_GLOSSARY_DOWNLOAD_TBX =
      "glossary-api/v2/accounts/{accountId}/glossaries/{glossaryId}/download?format=tbx";

  static final String ERROR_CANT_GET_FILES = "Can't get files";
  static final String ERROR_CANT_GET_SOURCE_STRINGS = "Can't get source strings";
  static final String ERROR_CANT_DOWNLOAD_FILE =
      "Can't download file: %s, projectId: %s, locale: %s";
  static final String ERROR_CANT_UPLOAD_FILE = "Can't upload file: %s";
  static final String ERROR_CANT_DELETE_FILE = "Can't delete file: %s";
  static final String ERROR_CANT_UPLOAD_CONTEXT = "Can't upload context: %s";
  static final String ERROR_CANT_DELETE_CONTEXT = "Can't delete context: %s";
  static final String ERROR_CANT_GET_CONTEXT = "Can't get context: %s";
  static final String ERROR_CANT_CREATE_BINDINGS = "Can't create bindings: %s";
  static final String ERROR_CANT_GET_GLOSSARY_DETAILS =
      "Can't retrieve glossary details accountId: %s, glossaryId: %s";
  static final String ERROR_CANT_DOWNLOAD_GLOSSARY_FILE_WITH_LOCALE =
      "Can't download glossary file accountId: %s, glossaryId: %s, locale: %s";
  static final String ERROR_CANT_DOWNLOAD_GLOSSARY_FILE =
      "Can't download glossary file accountId: %s, glossaryId: %s";
  static final String ERROR_CANT_GET_GLOSSARY_SOURCE_TERMS =
      "Can't retrieve glossary source terms accountId: %s, glossaryId: %s";
  static final String ERROR_CANT_GET_GLOSSARY_TARGET_TERMS =
      "Can't retrieve glossary target terms accountId: %s, glossaryId: %s, locale: %s";

  final ObjectMapper objectMapper;

  static final String API_SUCCESS_CODE = "SUCCESS";

  static final int LIMIT = 500;

  OAuth2RestTemplate oAuth2RestTemplate;

  RetryBackoffSpec retryConfiguration;

  public SmartlingClient(
      OAuth2RestTemplate oAuth2RestTemplate, RetryBackoffSpec retryConfiguration) {
    this.oAuth2RestTemplate = oAuth2RestTemplate;
    this.objectMapper = new ObjectMapper();

    this.retryConfiguration = retryConfiguration;
  }

  public RetryBackoffSpec getRetryConfiguration() {
    return this.retryConfiguration;
  }

  public Stream<StringInfo> getStringInfos(String projectId, String fileUri) {
    PageFetcherOffsetAndLimitSplitIterator<StringInfo> iterator =
        new PageFetcherOffsetAndLimitSplitIterator<StringInfo>(
            (offset, limit) -> {
              Items<StringInfo> stringInfoItems =
                  getSourceStrings(projectId, fileUri, offset, limit);

              return stringInfoItems.getItems();
            },
            LIMIT);

    return StreamSupport.stream(iterator, false);
  }

  public Items<File> getFiles(String projectId) {
    try {
      FilesResponse filesResponse =
          oAuth2RestTemplate.getForObject(API_FILES_LIST, FilesResponse.class, projectId);
      throwExceptionOnError(filesResponse, ERROR_CANT_GET_FILES);
      return filesResponse.getData();
    } catch (HttpClientErrorException httpClientErrorException) {
      throw wrapIntoSmartlingException(httpClientErrorException, ERROR_CANT_GET_FILES);
    }
  }

  public String downloadFile(
      String projectId,
      String locale,
      String fileUri,
      boolean includeOriginalStrings,
      RetrievalType retrievalType) {
    try {
      String file =
          oAuth2RestTemplate.getForObject(
              API_FILES_DOWNLOAD,
              String.class,
              projectId,
              locale,
              fileUri,
              includeOriginalStrings,
              retrievalType.getValue());
      return file;
    } catch (HttpClientErrorException e) {
      throw wrapIntoSmartlingException(e, ERROR_CANT_DOWNLOAD_FILE, fileUri, projectId, locale);
    }
  }

  public String downloadGlossaryFile(String accountId, String glossaryId) {
    try {
      String file =
          oAuth2RestTemplate.getForObject(
              API_GLOSSARY_DOWNLOAD_TBX, String.class, accountId, glossaryId);
      return file;
    } catch (HttpClientErrorException e) {
      throw wrapIntoSmartlingException(e, ERROR_CANT_DOWNLOAD_GLOSSARY_FILE, accountId, glossaryId);
    }
  }

  public String downloadSourceGlossaryFile(String accountId, String glossaryId, String locale) {
    try {
      String file =
          oAuth2RestTemplate.getForObject(
              API_GLOSSARY_SOURCE_TBX_DOWNLOAD, String.class, accountId, glossaryId, locale);
      return file;
    } catch (HttpClientErrorException e) {
      throw wrapIntoSmartlingException(
          e, ERROR_CANT_DOWNLOAD_GLOSSARY_FILE_WITH_LOCALE, accountId, glossaryId, locale);
    }
  }

  public String downloadGlossaryFileWithTranslations(
      String accountId, String glossaryId, String locale, String sourceLocale) {
    try {
      String file =
          oAuth2RestTemplate.getForObject(
              API_GLOSSARY_TRANSLATED_TBX_DOWNLOAD,
              String.class,
              accountId,
              glossaryId,
              locale,
              sourceLocale);
      return file;
    } catch (HttpClientErrorException e) {
      throw wrapIntoSmartlingException(
          e, ERROR_CANT_DOWNLOAD_GLOSSARY_FILE_WITH_LOCALE, accountId, glossaryId, locale);
    }
  }

  public String downloadPublishedFile(
      String projectId, String locale, String fileUri, boolean includeOriginalStrings) {
    return downloadFile(
        projectId, locale, fileUri, includeOriginalStrings, RetrievalType.PUBLISHED);
  }

  public void deleteFile(String projectId, String fileUri) {

    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("fileUri", fileUri);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

    try {
      Response response =
          oAuth2RestTemplate.postForObject(
              API_FILES_DELETE, requestEntity, Response.class, projectId);
      throwExceptionOnError(response, ERROR_CANT_DELETE_FILE, fileUri);
    } catch (HttpClientErrorException e) {
      throw wrapIntoSmartlingException(e, ERROR_CANT_DELETE_FILE, fileUri);
    }
  }

  public FileUploadResponse uploadFile(
      String projectId,
      String fileUri,
      String fileType,
      String fileContent,
      String placeholderFormat,
      String placeholderFormatCustom) {

    NamedByteArrayResource fileContentAsResource =
        new NamedByteArrayResource(fileContent.getBytes(Charsets.UTF_8), fileUri);

    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("fileUri", fileUri);
    body.add("fileType", fileType);
    body.add("smartling.placeholder_format", placeholderFormat);
    body.add("smartling.placeholder_format_custom", placeholderFormatCustom);
    body.add("smartling.instruction_comments_enabled", "on");
    body.add("file", fileContentAsResource);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

    try {
      FileUploadResponse response =
          oAuth2RestTemplate.postForObject(
              API_FILES_UPLOAD, requestEntity, FileUploadResponse.class, projectId);
      throwExceptionOnError(response, ERROR_CANT_UPLOAD_FILE, fileUri);
      return response;
    } catch (HttpClientErrorException e) {
      throw wrapIntoSmartlingException(e, ERROR_CANT_UPLOAD_FILE, fileUri);
    }
  }

  public FileUploadResponse uploadLocalizedFile(
      String projectId,
      String fileUri,
      String fileType,
      String localeId,
      String fileContent,
      String placeholderFormat,
      String placeholderFormatCustom) {

    NamedByteArrayResource fileContentAsResource =
        new NamedByteArrayResource(fileContent.getBytes(Charsets.UTF_8), fileUri);

    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("fileUri", fileUri);
    body.add("fileType", fileType);
    body.add("translationState", "PUBLISHED");
    body.add("overwrite", true);
    body.add("smartling.placeholder_format", placeholderFormat);
    body.add("smartling.placeholder_format_custom", placeholderFormatCustom);
    body.add("file", fileContentAsResource);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

    try {
      FileUploadResponse response =
          oAuth2RestTemplate.postForObject(
              API_FILES_UPLOAD_LOCALIZED,
              requestEntity,
              FileUploadResponse.class,
              projectId,
              localeId);
      throwExceptionOnError(response, ERROR_CANT_UPLOAD_FILE, fileUri);
      return response;
    } catch (HttpClientErrorException e) {
      throw wrapIntoSmartlingException(e, ERROR_CANT_UPLOAD_FILE, fileUri);
    }
  }

  public Items<StringInfo> getSourceStrings(
      String projectId, String fileUri, Integer offset, Integer limit)
      throws SmartlingClientException {
    try {
      SourceStringsResponse sourceStringsResponse =
          oAuth2RestTemplate.getForObject(
              API_SOURCE_STRINGS, SourceStringsResponse.class, projectId, fileUri, offset, limit);
      throwExceptionOnError(sourceStringsResponse, ERROR_CANT_GET_SOURCE_STRINGS);
      return sourceStringsResponse.getData();
    } catch (HttpClientErrorException e) {
      throw wrapIntoSmartlingException(e, ERROR_CANT_GET_SOURCE_STRINGS);
    }
  }

  public Context uploadContext(String projectId, String name, byte[] content) {

    ByteArrayResource contentAsResource = new NamedByteArrayResource(content, name);

    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("content", contentAsResource);
    body.add("name", name);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

    try {
      ContextResponse contextResponse =
          oAuth2RestTemplate.postForObject(
              API_CONTEXTS, requestEntity, ContextResponse.class, projectId);
      throwExceptionOnError(contextResponse, ERROR_CANT_UPLOAD_CONTEXT, name);
      return contextResponse.getData();
    } catch (HttpClientErrorException e) {
      throw wrapIntoSmartlingException(e, ERROR_CANT_UPLOAD_CONTEXT, name);
    }
  }

  public void createBindings(Bindings bindings, String projectId) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
    HttpEntity<Bindings> requestEntity = new HttpEntity<>(bindings, headers);
    try {
      String s =
          oAuth2RestTemplate.postForObject(API_BINDINGS, requestEntity, String.class, projectId);
      logger.debug("create binding: {}", s);
    } catch (HttpClientErrorException e) {
      throw wrapIntoSmartlingException(
          e, ERROR_CANT_CREATE_BINDINGS, objectMapper.writeValueAsStringUnchecked(bindings));
    }
  }

  public GlossaryDetails getGlossaryDetails(String accountId, String glossaryId) {
    try {
      GetGlossaryDetailsResponse getGlossaryDetailsResponse =
          oAuth2RestTemplate.getForObject(
              API_GLOSSARY_DETAILS, GetGlossaryDetailsResponse.class, accountId, glossaryId);
      throwExceptionOnError(
          getGlossaryDetailsResponse, ERROR_CANT_GET_GLOSSARY_DETAILS, accountId, glossaryId);
      return getGlossaryDetailsResponse.getData();
    } catch (HttpClientErrorException e) {
      throw wrapIntoSmartlingException(e, ERROR_CANT_GET_GLOSSARY_DETAILS, accountId, glossaryId);
    }
  }

  /** To throw an exception when Smartling returns a 200 but still is not successful */
  <T> void throwExceptionOnError(Response<T> response, String msg, Object... vars) {
    if (!API_SUCCESS_CODE.equals(response.getCode())) {
      String errorsAsString = objectMapper.writeValueAsStringUnchecked(response.getErrors());
      throw new SmartlingClientException(
          String.format(msg, vars)
              + "(code: "
              + response.getCode()
              + ", errors: "
              + errorsAsString
              + ")");
    }
  }

  /**
   * For error raised through HTTP error.
   *
   * <p>Note that 200 is not always success, {@see throwExceptionOnError}
   */
  SmartlingClientException wrapIntoSmartlingException(
      HttpClientErrorException httpClientErrorException, String messageSummary, Object... vars)
      throws SmartlingClientException {
    String msg =
        String.format(messageSummary, vars)
            + "\nMessage: "
            + httpClientErrorException.getMessage()
            + "\nBody: "
            + httpClientErrorException.getResponseBodyAsString();
    return new SmartlingClientException(msg, httpClientErrorException);
  }

  static class NamedByteArrayResource extends ByteArrayResource {
    private final String filename;

    public NamedByteArrayResource(byte[] content, String filename) {
      super(content);
      this.filename = getNameForMultipart(filename);
    }

    /**
     * For some reason (not investigated) spring doesn't map "PNG" (uppercase) to proper type when
     * doing multipart
     *
     * <p>it uses: Content-Disposition: form-data; name="content"; filename="caseissuewithpng.PNG"
     * Content-Type: application/octet-stream
     *
     * <p>instead of: Content-Disposition: form-data; name="content";
     * filename="caseissuewithpng.png" Content-Type: image/x-png
     *
     * <p>So make the filename lower case here to have proper content-type.
     *
     * <p>This should not change the filename uploaded to Smartling - but need to keep an eye on it.
     * Might be better to look more into doing that with properly in Spring
     */
    String getNameForMultipart(String name) {
      return name.replaceAll("PNG$", "png");
    }

    @Override
    public String getFilename() {
      return filename;
    }
  }

  public void deleteContext(String projectId, String contextId) {
    try {
      oAuth2RestTemplate.delete(
          API_CONTEXTS_DETAILS.replace("{projectId}", projectId).replace("{contextId}", contextId));
    } catch (HttpClientErrorException e) {
      throw wrapIntoSmartlingException(e, ERROR_CANT_DELETE_CONTEXT, contextId);
    }
  }

  public Context getContext(String projectId, String contextId) {
    try {
      ContextResponse contextResponse =
          oAuth2RestTemplate.getForObject(
              API_CONTEXTS_DETAILS, ContextResponse.class, projectId, contextId);

      throwExceptionOnError(contextResponse, ERROR_CANT_GET_CONTEXT, contextId);
      return contextResponse.getData();
    } catch (HttpClientErrorException e) {
      throw wrapIntoSmartlingException(e, ERROR_CANT_GET_CONTEXT, contextId);
    }
  }
}
