package org.supla.android;
/*
 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

 */

import android.os.AsyncTask;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ESPConfigureTask extends AsyncTask<String, Integer, ESPConfigureTask.ConfigResult> {

    public static final int RESULT_PARAM_ERROR   = -3;
    public static final int RESULT_COMPAT_ERROR  = -2;
    public static final int RESULT_CONN_ERROR    = -1;
    public static final int RESULT_FAILED        =  0;
    public static final int RESULT_SUCCESS       =  1;

    private AsyncResponse delegate;

    public interface AsyncResponse {
        void espConfigFinished(ConfigResult result);
    }

    public class ConfigResult {
        int resultCode;

        String deviceLastState;
        String deviceFirmwareVersion;
        String deviceGUID;
        String deviceMAC;
    }

    public void setDelegate(AsyncResponse delegate) {
        this.delegate = delegate;
    }

    @Override
    protected ConfigResult doInBackground(String... params) {

        ConfigResult result = new ConfigResult();

        if ( params.length != 4
                || params[0].isEmpty()
                || params[2].isEmpty()
                || params[3].isEmpty() ) {

            result.resultCode = RESULT_PARAM_ERROR;
            return result;
        }

        int retryCount = 10;
        Map<String, String> fieldMap = new HashMap<String, String>();

        while(retryCount > 0)
            try {

                Thread.sleep(1000);
                Document doc = Jsoup.connect("http://192.168.4.1").get();

                Elements inputs = doc.getElementsByTag("input");

                if ( inputs != null ) {
                    for (Element element : inputs) {
                        fieldMap.put(element.attr("name"), element.val());
                    };
                }

                Elements sel = doc.getElementsByTag("select");

                if ( sel != null ) {
                    for (Element element : sel) {
                        Elements option = element.select("option[selected]");

                        if ( option != null && option.hasAttr("selected") ) {
                            fieldMap.put(element.attr("name"), option.val());
                        }
                    };
                }

                Elements span = doc.getElementsByTag("span");
                if ( span != null ) {

                    for (Element element : span) {

                        if ( element.html().contains("LAST STATE") ) {

                            Pattern mPattern = Pattern.compile("^LAST\\ STATE:\\ (.*)\\<br\\>Firmware:\\ (.*)\\<br\\>GUID:\\ (.*)\\<br\\>MAC:\\ (.*)$");

                            Matcher matcher = mPattern.matcher(element.html());
                            if(matcher.find() && matcher.groupCount() == 4)  {
                                for(int a=0;a<=matcher.groupCount();a++)
                                    Trace.d("MATCH", Integer.toString(a)+". "+matcher.group(a));

                                result.deviceLastState = matcher.group(1);
                                result.deviceFirmwareVersion  = matcher.group(2);
                                result.deviceGUID = matcher.group(3);
                                result.deviceMAC = matcher.group(4);
                            }

                            break;
                        }
                    }

                }

                retryCount = -1;

            } catch (IOException e) {
                e.printStackTrace();
                retryCount--;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        if ( retryCount > -1 ) {

            result.resultCode = RESULT_CONN_ERROR;
            return result;
        }


        if ( fieldMap.get("sid") == null
                || fieldMap.get("wpw") == null
                || fieldMap.get("svr") == null
                || fieldMap.get("eml") == null
                || result.deviceFirmwareVersion.isEmpty() ) {

            result.resultCode = RESULT_COMPAT_ERROR;
            return result;
        }

        fieldMap.put("sid", params[0]);
        fieldMap.put("wpw", params[1]);
        fieldMap.put("svr", params[2]);
        fieldMap.put("eml", params[3]);

        retryCount = 3;

        while(retryCount > 0)
        try {
            Thread.sleep(1000);

            Document doc = (Document) Jsoup.connect("http://192.168.4.1")
                    .data(fieldMap)
                    .referrer("http://192.168.4.1").method(Connection.Method.POST).execute().parse();

            Element msg = doc.getElementById("msg");
            if ( msg != null && msg.html().toLowerCase().contains("data saved") ) {

                Thread.sleep(1000);

                fieldMap.put("rbt", "1"); // reboot

                Jsoup.connect("http://192.168.4.1")
                        .data(fieldMap)
                        .referrer("http://192.168.4.1").method(Connection.Method.POST).execute();


                result.resultCode = RESULT_SUCCESS;
                return result;
            }

        } catch (IOException e) {
            e.printStackTrace();
            retryCount--;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        result.resultCode = RESULT_FAILED;
        return result;
    }

    @Override
    protected void onPostExecute(ConfigResult result) {
        delegate.espConfigFinished(result);
    }
}