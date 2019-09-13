package com.zeroturnaround.jrebel;

import java.io.IOException;
import java.sql.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;

public class SigningHandler extends AbstractHandler {

  private final AmazonS3 s3;

  public SigningHandler(AmazonS3 s3) {
    this.s3 = s3;
  }

  @Override
  public void handle(String s, Request request, HttpServletRequest httpServletRequest, HttpServletResponse resp) throws IOException {
    String signedUrl = signUrl(toKey(request));
    resp.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
    resp.setHeader("Location", signedUrl);
    request.setHandled(true);
  }

  private String signUrl(String key) {
    GeneratePresignedUrlRequest signReq = new GeneratePresignedUrlRequest("share.jc.zt", key);
    signReq.setMethod(HttpMethod.GET);
    signReq.setExpiration(Date.from(Instant.now().plus(8, ChronoUnit.HOURS)));
    return s3.generatePresignedUrl(signReq).toExternalForm();
  }

  private String toKey(Request request) {
    String path = request.getPathInfo();
    while (path.startsWith("/"))
      path = path.substring(1);
    return path;
  }
}
