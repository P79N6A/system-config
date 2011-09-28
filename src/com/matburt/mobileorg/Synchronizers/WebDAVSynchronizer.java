package com.matburt.mobileorg.Synchronizers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;

import android.content.Context;
import android.util.Log;

public class WebDAVSynchronizer extends Synchronizer
{
    private boolean pushedStageFile = false;

    public WebDAVSynchronizer(Context parentContext) {
    	super(parentContext);
    }
    
    public boolean isConfigured() {
        if (this.appSettings.getString("webUrl","").equals(""))
            return false;
        return true;
    }

    public void push() throws IOException  {
        String urlActual = this.getRootUrl() + "mobileorg.org";
        BufferedReader reader = this.getReadHandle("mobileorg.org");
        String fileContents = "";
        this.pushedStageFile = false;
        String thisLine = "";

        if (reader == null) {
            return;
        }
            while ((thisLine = reader.readLine()) != null) {
                fileContents += thisLine + "\n";
            }
     

        DefaultHttpClient httpC = this.createConnection(
                                    this.appSettings.getString("webUser", ""),
                                    this.appSettings.getString("webPass", ""));

        this.appendUrlFile(urlActual, httpC, fileContents);

        if (this.pushedStageFile) {
            this.removeFile("mobileorg.org");
        }
    }

    public void pull() throws IOException {
        Pattern checkUrl = Pattern.compile("http.*\\.(?:org|txt)$");
        String url = this.appSettings.getString("webUrl", "");
        if (!checkUrl.matcher(url).find()) {
//        	throw new ReportableError(
//            		r.getString(R.string.error_bad_url, url),
//            		null);
        }

        //Get the index org file
        String masterStr = this.fetchOrgFileString(url);
        if (masterStr.equals("")) {
//            throw new ReportableError(
//            		r.getString(R.string.error_file_not_found, url),
//            		null);
        }
        HashMap<String, String> masterList = this.getOrgFilesFromMaster(masterStr);
        ArrayList<HashMap<String, Boolean>> todoLists = this.getTodos(masterStr);
        ArrayList<ArrayList<String>> priorityLists = this.getPriorities(masterStr);
        this.appdb.setTodoList(todoLists);
        this.appdb.setPriorityList(priorityLists);
        String urlActual = this.getRootUrl();

        //Get checksums file
        masterStr = this.fetchOrgFileString(urlActual + "checksums.dat");
        HashMap<String, String> newChecksums = this.getChecksums(masterStr);
        HashMap<String, String> oldChecksums = this.appdb.getChecksums();

        //Get other org files
        for (String key : masterList.keySet()) {
            if (oldChecksums.containsKey(key) &&
                newChecksums.containsKey(key) &&
                oldChecksums.get(key).equals(newChecksums.get(key)))
                continue;
            Log.d(LT, "Fetching: " +
                  key + ": " + urlActual + masterList.get(key));
            this.fetchAndSaveOrgFile(urlActual + masterList.get(key),
                                     masterList.get(key));
            this.appdb.addOrUpdateFile(masterList.get(key),
                                       key,
                                       newChecksums.get(key));
        }
    }

    public BufferedReader fetchOrgFile(String orgUrl) throws IOException {
        DefaultHttpClient httpC = this.createConnection(
                                    this.appSettings.getString("webUser", ""),
                                    this.appSettings.getString("webPass", ""));
        InputStream mainFile;
            mainFile = this.getUrlStream(orgUrl, httpC);

        if (mainFile == null) {
            return null;
        }
        return new BufferedReader(new InputStreamReader(mainFile));
    }

    private String getRootUrl() {
        URL manageUrl = null;
        try {
            manageUrl = new URL(this.appSettings.getString("webUrl", ""));
        }
        catch (MalformedURLException e) {
//            throw new ReportableError(
//            		r.getString(R.string.error_bad_url,
//            				(manageUrl == null) ? "" : manageUrl.toString()),
//            		e);
        }

        String urlPath =  manageUrl.getPath();
        String[] pathElements = urlPath.split("/");
        String directoryActual = "/";
        if (pathElements.length > 1) {
            for (int idx = 0; idx < pathElements.length - 1; idx++) {
                if (pathElements[idx].length() > 0) {
                    directoryActual += pathElements[idx] + "/";
                }
            }
        }
        return manageUrl.getProtocol() + "://" +
            manageUrl.getAuthority() + directoryActual;
    }

    private DefaultHttpClient createConnection(String user, String password) {
        DefaultHttpClient httpClient = new DefaultHttpClient();
        HttpParams params = httpClient.getParams();
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register (new Scheme ("http",
                                             PlainSocketFactory.getSocketFactory (), 80));
        SSLSocketFactory sslSocketFactory = SSLSocketFactory.getSocketFactory();
        sslSocketFactory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        schemeRegistry.register (new Scheme ("https",
                                             sslSocketFactory, 443));
        ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager (
                                                  params, schemeRegistry);

        UsernamePasswordCredentials bCred = new UsernamePasswordCredentials(user, password);
        BasicCredentialsProvider cProvider = new BasicCredentialsProvider();
        cProvider.setCredentials(AuthScope.ANY, bCred);

        params.setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, false);
        httpClient.setParams(params);

        DefaultHttpClient nHttpClient = new DefaultHttpClient(cm, params);
        nHttpClient.setCredentialsProvider(cProvider);
        return nHttpClient;
    }

    private InputStream getUrlStream(String url, DefaultHttpClient httpClient) throws IOException {
            HttpResponse res = httpClient.execute(new HttpGet(url));
            StatusLine status = res.getStatusLine();
            if (status.getStatusCode() == 401) {
//                throw new ReportableError(r.getString(R.string.error_url_fetch_detail,
//                                                      url,
//                                                      "Invalid username or password"),
//                                          null);
            }
            if (status.getStatusCode() == 404) {
                return null;
            }

            if (status.getStatusCode() < 200 || status.getStatusCode() > 299) {
//            	throw new ReportableError(
//            			r.getString(R.string.error_url_fetch_detail,
//                                    url,
//                                    status.getReasonPhrase()),
//            			null);
            }
            return res.getEntity().getContent();
    }

    private void putUrlFile(String url,
                           DefaultHttpClient httpClient,
                           String content) {
        try {
            HttpPut httpPut = new HttpPut(url);
            httpPut.setEntity(new StringEntity(content, "UTF-8"));
            HttpResponse response = httpClient.execute(httpPut);
            StatusLine statResp = response.getStatusLine();
            int statCode = statResp.getStatusCode();
            if (statCode >= 400) {
                this.pushedStageFile = false;
//                throw new ReportableError(r.getString(R.string.error_url_put_detail,
//                                                      url,
//                                                      "Server returned code: " + Integer.toString(statCode)),
//                                          null);
            } else {
                this.pushedStageFile = true;
            }

            httpClient.getConnectionManager().shutdown();
        }
        catch (UnsupportedEncodingException e) {
//        	throw new ReportableError(
//        			r.getString(R.string.error_unsupported_encoding, "mobileorg.org"),
//        			e);
        }
        catch (IOException e) {
//        	throw new ReportableError(
//        			r.getString(R.string.error_url_put, url),
//        			e);
        }
    }

    private void appendUrlFile(String url,
    							DefaultHttpClient httpClient,
    							String content) throws IOException {
    	String originalContent = this.fetchOrgFileString(url);
    	String newContent = originalContent + '\n' + content;
    	this.putUrlFile(url, httpClient, newContent);
    }
}

