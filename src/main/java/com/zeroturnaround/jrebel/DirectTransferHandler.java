package com.zeroturnaround.jrebel;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.util.IOUtils;

public class DirectTransferHandler extends AbstractHandler {

  private static Logger log = LoggerFactory.getLogger(DirectTransferHandler.class);

  private final AmazonS3 s3;
  private final List<String> directPrefixes;

  public DirectTransferHandler(AmazonS3 s3, List<String> directPrefixes) {
    this.s3 = s3;
    this.directPrefixes = directPrefixes;
  }

  @Override
  public void handle(String s, Request request, HttpServletRequest httpServletRequest, HttpServletResponse response) throws IOException, ServletException {
    if (!anyMatch(directPrefixes, request.getPathInfo()))
      return;

    try (S3Object object = s3.getObject("share.jc.zt", toKey(request))) {
      response.setStatus(HttpServletResponse.SC_OK);
      response.setContentLengthLong(object.getObjectMetadata().getContentLength());
      IOUtils.copy(object.getObjectContent(), response.getOutputStream());
    } catch (AmazonS3Exception e) {
      if (e.getStatusCode() != 404)
        log.warn(httpServletRequest.getRequestURI() + ": " + e.toString());
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      response.getWriter().write(e.getMessage());
    }
    request.setHandled(true);
  }

  private boolean anyMatch(List<String> prefixes, String path) {
    for (String prefix : prefixes) {
      if (path.startsWith(prefix))
        return true;
    }
    return false;
  }

  private String toKey(Request request) {
    String path = request.getPathInfo();
    while (path.startsWith("/"))
      path = path.substring(1);
    return path;
  }
}
