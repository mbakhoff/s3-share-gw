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
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;

public class SigningHandler extends AbstractHandler {

  private final AmazonS3 s3 = AmazonS3Client.builder()
      .withRegion(Regions.US_EAST_1)
      .withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
      .withPathStyleAccessEnabled(true)
      .build();

  @Override
  public void handle(String s, Request request, HttpServletRequest httpServletRequest, HttpServletResponse resp) throws IOException {
    String signedUrl = signUrl(request.getPathInfo());
    resp.setHeader("Location", signedUrl);
    resp.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
    request.setHandled(true);
  }

  private String signUrl(String path) {
    while (path.startsWith("/"))
      path = path.substring(1);
    var signReq = new GeneratePresignedUrlRequest("share.jc.zt", path);
    signReq.setMethod(HttpMethod.GET);
    signReq.setExpiration(Date.from(Instant.now().plus(8, ChronoUnit.HOURS)));
    return s3.generatePresignedUrl(signReq).toExternalForm();
  }
}
