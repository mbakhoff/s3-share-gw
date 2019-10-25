package com.zeroturnaround.jrebel;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

public class S3AuthProxy {

  public static void main(String[] args) throws Exception {
    int port = Integer.parseInt(System.getProperty("server.port", "8080"));
    List<String> directPrefixes = parsePrefixes(System.getProperty("direct"));
    AmazonS3 s3 = AmazonS3ClientBuilder.standard()
        .withRegion(Regions.US_EAST_1)
        .withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
        .withPathStyleAccessEnabled(true)
        .build();

    Server server = new Server(port);
    server.setHandler(new HandlerList(
        new ListingHandler(s3),
        new DirectTransferHandler(s3, directPrefixes),
        new SigningHandler(s3)));
    server.start();
    server.join();
  }

  private static List<String> parsePrefixes(String directPrefixes) {
    return directPrefixes != null
        ? Arrays.asList(directPrefixes.split(","))
        : Collections.emptyList();
  }
}
