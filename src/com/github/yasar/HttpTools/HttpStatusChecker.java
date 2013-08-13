/*
 * HttpStatusCodes
 * 
 * First version
 *
 * 2013-08-12
 * 
 * This code is copyrighted under Creative Commons Attribution-ShareAlike 3.0 Unported
 * Copyright (c) Yaşar Arabacı http://creativecommons.org/licenses/by-sa/3.0/deed.en
 */
package com.github.yasar.HttpTools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A java class to fetch Http status of multiple urls in parallel.
 * The HttpStatusCodes class aims to provide an easy way to get HTTP Status
 * codes from a list of urls.
 *
 * @author Yaşar Arabacı <yasar11732@gmail.com>
 */
public class HttpStatusChecker {

    private BufferedReader input;
    private int numThreads;

    /**
     * When invoked from command line, HttpStatusCodes will read urls from the
     * files whose names were passed in arguements and print results to
     * standart output.
     *
     * @param args
     */
    public static void main(String[] args) {
        for (String arg : args) {
            HttpStatusChecker app;
            try {
                app = new HttpStatusChecker(new FileReader(new File(arg)), 16);
            } catch (FileNotFoundException e) {
                System.err.println("File doesn't exist: " + e.getMessage());
                continue;
            }
            Map<String, String> results = app.runCheck();
            for (String key : results.keySet()) {
                System.out.println(key + " " + results.get(key));
            }
        }
    }
    
    /**
     * Container for pairs of urls and statuses.
     */
    private class UrlAndCode {

        public String url;
        public String status;

        public UrlAndCode(String url, String statusCode) {
            this.url = url;
            this.status = statusCode;
        }
    }

    /**
     * This class gets executed in a seperate Thread to
     * get status of a single url
     */
    private class GetStatus implements Callable<UrlAndCode> {

        private String location;
        private int timeout;

        public GetStatus(String location, int timeout) {
            this.location = location;
            this.timeout = timeout;
        }

        @Override
        public UrlAndCode call() {
            URL url;
            // to fix Malformed urls
            if (!location.startsWith("http")) {
                location = "http://" + location;
            }
            
            // url can still be malformed
            try {
                url = new URL(location);
            } catch (MalformedURLException e) {
                return new UrlAndCode(location, e.getMessage());
            }
            
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) url.openConnection();
                HttpURLConnection.setFollowRedirects(false);
                con.setConnectTimeout(timeout * 1000);
                con.setRequestMethod("HEAD");
                return new UrlAndCode(location, new Integer(con.getResponseCode()).toString());
            } catch (Exception e) {
                /*
                 * Exception will be returned along with url, otherwise we can't
                 * know which Exception on which url. Otherwise, it will be hard to
                 * track Exceptions.
                 */
                return new UrlAndCode(location, e.getClass() + ":" + e.getMessage());
            } finally {
                if (con != null) {
                    con.disconnect();
                }
            }
        }
    }
    /**
     * 
     * @param r Urls will be read from this Reader line by line
     * @param numberOfThreads number of threads to use
     */
    public HttpStatusChecker(Reader r, int numberOfThreads) {
        input = new BufferedReader(r);
        numThreads = numberOfThreads;
    }
    
    /**
     * see {@link #HttpStatusCodes(Reader,int) HttpStatusCodes}. Timeout will
     * default to 4 seconds.
     */
    public HttpStatusChecker(Reader r) {
        this(r, 4);
    }
    
    /** 
     * calls {@link #get(int) get} with default timeout of 2 seconds.
     */
    public Map<String, String> runCheck() {
        return runCheck(2);
    }
    
    /**
     * When this method is invoked, urls will be distributed among worker threads
     * to be connected. After all urls have been tried, this method will return.
     * @param timeout How many seconds to wait before getting a response from connection.
     * @return A Map of <String,String> keys hold urls, values hold status codes or error messages.
     */
    public Map<String, String> runCheck(int timeout) {
        Map<String, String> results = new HashMap<>();
        ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);
        CompletionService<UrlAndCode> pool = new ExecutorCompletionService<>(threadPool);
        String line;
        int numWorks = 0;

        try {
            while ((line = input.readLine()) != null) {
                pool.submit(new GetStatus(line, timeout));
                numWorks++;
            }
        } catch (IOException e) {
            Logger.getLogger(HttpStatusChecker.class.getName()).log(Level.SEVERE, null, e);
            threadPool.shutdown();
            return null;
        } finally {
            try {
                input.close();
            } catch (IOException e) {
                /*
                 * Closing the input stream is not critical for us, will log
                 * and continue
                 */
                Logger.getLogger(HttpStatusChecker.class.getName()).log(Level.SEVERE, null, e);
            }
        }
        
        // Uncomment for debugging.
        //System.out.println(numWorks + " jobs added.");
        
        while (numWorks > 0) {
            UrlAndCode result;
            try {
                result = pool.take().get();
            } catch (InterruptedException | ExecutionException e) {
                Logger.getLogger(HttpStatusChecker.class.getName()).log(Level.SEVERE, null, e);
                threadPool.shutdown();
                return null;
            }
            results.put(result.url, result.status);
            numWorks--;
            // Uncomment for debugging
            // System.out.println(numWorks + " jobs remaining.");
        }
        threadPool.shutdown();
        return results;
    }
}
