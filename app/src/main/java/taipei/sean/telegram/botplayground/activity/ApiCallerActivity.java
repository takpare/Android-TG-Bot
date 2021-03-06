package taipei.sean.telegram.botplayground.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import taipei.sean.telegram.botplayground.InstantComplete;
import taipei.sean.telegram.botplayground.R;
import taipei.sean.telegram.botplayground.SeanAdapter;
import taipei.sean.telegram.botplayground.SeanDBHelper;
import taipei.sean.telegram.botplayground.TelegramAPI;
import taipei.sean.telegram.botplayground.adapter.ApiCallerAdapter;

public class ApiCallerActivity extends AppCompatActivity {
    final private int _dbVer = 4;
    private SeanDBHelper db;
    private String _token;
    private TelegramAPI _api;
    private JSONObject apiMethods;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_api_caller);

        db = new SeanDBHelper(this, "data.db", null, _dbVer);

        Intent intent = getIntent();
        Uri uri = intent.getData();
        if (null != uri) {
            String path = uri.getPath();

            Pattern p = Pattern.compile("/bot(" + getString(R.string.bot_token_regex) + ")/.+");
            Matcher m = p.matcher(path);
            if (m.matches()) {
                _token = m.group(1);
                db.insertToken(_token, _token);
            } else {
                Log.e("caller", "no token with intent");
                finish();
                return;
            }
        } else {
            try {
                Bundle bundle = getIntent().getExtras();
                _token = bundle.getString("token");
            } catch (NullPointerException e) {
                Log.e("caller", "bundle error", e);
                finish();
            }
        }

        _api = new TelegramAPI(this, _token);

        final InstantComplete methodView = (InstantComplete) findViewById(R.id.api_caller_method);
        final Button submitButton = (Button) findViewById(R.id.api_caller_submit);

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                submit();
            }
        });

        ArrayList<String> botApiMethodsList = new ArrayList<String>() {
        };
        apiMethods = loadMethods();

        if (null == apiMethods) {
            Log.e("caller", "no methods");
            return;
        }

        Iterator<String> temp = apiMethods.keys();
        while (temp.hasNext()) {
            String key = temp.next();
            botApiMethodsList.add(key);
        }

        final SeanAdapter<String> adapter = new SeanAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, botApiMethodsList);
        methodView.setAdapter(adapter);

        methodView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                updateMethod();
            }
        });

        if (null != uri) {
            String path = uri.getPath();

            Pattern p = Pattern.compile("/bot" + getString(R.string.bot_token_regex) + "/([A-Za-z]+)");
            Matcher m = p.matcher(path);
            if (m.matches()) {
                String method = m.group(1);
                db.updateParam("_method", method);
            }

            Set<String> args = uri.getQueryParameterNames();
            for (Object argNameObj : args) {
                String argName = argNameObj.toString();
                String argVal = uri.getQueryParameter(argName);
                db.updateParam(argName, argVal);
            }
        }

        String method = db.getParam("_method");
        if (apiMethods.has(method))
            methodView.setText(method);

        updateMethod();
    }

    private void updateMethod() {
        final InstantComplete methodView = (InstantComplete) findViewById(R.id.api_caller_method);
        final RecyclerView paramList = (RecyclerView) findViewById(R.id.api_caller_inputs);
        final String method = methodView.getText().toString();

        JSONObject methodData;
        JSONObject paramData;

        if (null == apiMethods) {
            Log.d("caller", "no methods");
            return;
        }

        if (!apiMethods.has(method)) {
            methodView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
            ViewGroup.LayoutParams layoutParams = paramList.getLayoutParams();
            layoutParams.height = 0;
            paramList.setLayoutParams(layoutParams);
            return;
        }

        methodView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_star_border_black_24dp, 0);

        try {
            methodData = apiMethods.getJSONObject(method);
        } catch (JSONException e) {
            Log.e("caller", apiMethods.toString(), e);
            return;
        }

        if (methodData.has("description")) {
            try {
                final String desc = methodData.getString("description");
                methodView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View view) {
                        Snackbar.make(view, desc, Snackbar.LENGTH_LONG).show();
                        return false;
                    }
                });
            } catch (JSONException e) {
                Log.e("caller", "method description", e);
            }
        }

        if (methodData.has("params")) {
            try {
                paramData = methodData.getJSONObject("params");
            } catch (JSONException e) {
                Log.e("caller", methodData.toString(), e);
                return;
            }
        } else {
            Log.e("caller", "No params: " + method);
            return;
        }

        final ApiCallerAdapter apiCallerAdapter = new ApiCallerAdapter(getApplicationContext());

        try {
            Iterator<String> temp = paramData.keys();
            while (temp.hasNext()) {
                String key = temp.next();
                JSONObject value = paramData.getJSONObject(key);
                apiCallerAdapter.addData(key, value);
            }
        } catch (JSONException e) {
            Log.e("caller", "parse", e);
        }

        paramList.setAdapter(apiCallerAdapter);
        paramList.setLayoutManager(new StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL));
        paramList.setItemViewCacheSize(paramData.length());

        db.updateParam("_method", method);

        paramList.post(new Runnable() {
            @Override
            public void run() {
                apiCallerAdapter.fitView(paramList);
            }
        });
    }

    private void submit() {
        final InstantComplete methodView = (InstantComplete) findViewById(R.id.api_caller_method);
        final RecyclerView paramList = (RecyclerView) findViewById(R.id.api_caller_inputs);
        final TextView resultView = (TextView) findViewById(R.id.api_caller_result);

        String method = methodView.getText().toString();
        JSONObject jsonObject = new JSONObject();

        final ApiCallerAdapter paramAdapter = (ApiCallerAdapter) paramList.getAdapter();

        if (null == paramAdapter) {
            _api.callApi(method, resultView, null);
            return;
        }

        final int paramHeight = paramList.getHeight();
        if (paramHeight == 0) {
            _api.callApi(method, resultView, null);
            return;
        }

        final int inputCount = paramAdapter.getItemCount();
        for (int i = 0; i < inputCount; i++) {
            String name = paramAdapter.getName(i);
            String value = paramAdapter.getValue(i);

            if (null == value)
                continue;
            if (Objects.equals(value, ""))
                continue;

            db.insertFav(name, value, method);

            try {
                JSONObject valueJson = new JSONObject(value);   // if can be JSON Object
                jsonObject.put(name, valueJson);   // treat as JSON Object
            } catch (JSONException e1) {
                try {
                    JSONArray valueJson = new JSONArray(value);   // if not Object, but can be Array
                    jsonObject.put(name, valueJson);   // treat as Array
                } catch (JSONException e2) {
                    try {
                        jsonObject.put(name, value);   // not JSON, treat as string
                    } catch (JSONException e3) {
                        Log.e("caller", "put", e3);   // Can't put value to jsonObject
                    }
                }
            }
        }

        _api.callApi(method, resultView, jsonObject);
    }

    public JSONObject loadMethods() {
        final String lang = getString(R.string.lang_code);
        JSONObject json = new JSONObject();

        try {
            InputStream is = getAssets().open("api-methods.json");

            int size = is.available();
            byte[] buffer = new byte[size];

            if (is.read(buffer) < 0)
                return null;

            is.close();
            String jsonStr = new String(buffer, "UTF-8");

            try {
                json = new JSONObject(jsonStr);
            } catch (JSONException e) {
                Log.e("caller", "parse", e);
            }
        } catch (IOException e) {
            Log.e("caller", "get", e);
        }

        try {
            InputStream is = getAssets().open("api-methods-" + lang + ".json");

            int size = is.available();
            byte[] buffer = new byte[size];

            if (is.read(buffer) < 0)
                return null;

            is.close();
            String jsonStr = new String(buffer, "UTF-8");

            try {
                JSONObject localeJson = new JSONObject(jsonStr);

                Iterator<String> methods = localeJson.keys();
                while (methods.hasNext()) {
                    String methodName = methods.next();
                    JSONObject method = json.getJSONObject(methodName);
                    JSONObject localeMethod = localeJson.getJSONObject(methodName);

                    if (localeMethod.has("description")) {
                        String methodDesc = localeMethod.getString("description");
                        method.put("description", methodDesc);
                    }

                    if (localeMethod.has("params")) {
                        JSONObject params = method.getJSONObject("params");
                        JSONObject localeParams = localeMethod.getJSONObject("params");

                        Iterator<String> paramNames = localeParams.keys();
                        while (paramNames.hasNext()) {
                            String paramName = paramNames.next();
                            JSONObject param = params.getJSONObject(paramName);
                            String localeParam = localeParams.getString(paramName);

                            param.put("description", localeParam);
                        }
                    }
                }
            } catch (JSONException e) {
                Log.e("caller", "parse locale", e);
            }
        } catch (IOException e) {
            Log.e("caller", "get locale", e);
        }

        return json;
    }
}
