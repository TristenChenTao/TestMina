package com.esgame.testmina;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.os.Handler;
import android.widget.EditText;
import android.widget.TextView;


public class MainActivity extends AppCompatActivity {

    private Handler uiHandler = new Handler();

    private TextView mTextView;
    private EditText mEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        mEditText = (EditText) findViewById(R.id.messageSend);

        mTextView = (TextView)findViewById(R.id.textView);

        SocketManager.getInstance().mSocketManagerHandler = new SocketManager.SocketManagerHandler() {
            @Override
            public void messageReceived(String text) {
                System.out.println("messageReceived is "+text);

                final String finalText = text + "\n";

                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        MainActivity.this.mTextView.append(finalText);
                    }
                };
                uiHandler.post(runnable);
            }
        };

        SocketManager.getInstance().startConnect();

        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SocketManager.getInstance().sendMessage(mEditText.getText().toString());
            }
        });



    }
}

