package com.bosonshiggs.chatflow;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;

import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;

import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.util.MediaUtil;
import com.google.appinventor.components.runtime.*;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

import java.util.List;
import java.util.ArrayList;

import com.google.gson.Gson;
import com.google.appinventor.components.runtime.util.YailList;

@DesignerComponent(
        version = 23,
        description = "Chat View Extension with enhanced features including styling, editing, and exporting.",
        category = ComponentCategory.EXTENSION,
        nonVisible = true,
        iconName = "aiwebres/icon.png"
)
@SimpleObject(external = true)
public class ChatFlow extends AndroidNonvisibleComponent {

    private Context context;
    private ComponentContainer container;
    private LinearLayout chatLayout;
    private ScrollView scrollView;
    private boolean loggingEnabled = false;
    private HashMap<String, MessageData> messagesMap = new HashMap<>();

    public ChatFlow(ComponentContainer container) {
        super(container.$form());
        this.context = container.$context();
        this.container = container;
        scrollView = new ScrollView(context);
        chatLayout = new LinearLayout(context);
        chatLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(chatLayout);
    }

    @SimpleFunction(description = "Initializes the Chat View within a container, such as a HorizontalArrangement or VerticalArrangement.")
    public void Initialize(AndroidViewComponent container) {
        try {
            ViewGroup viewGroup = (ViewGroup) container.getView();
            viewGroup.addView(scrollView);
            logMessage("ChatFlow initialized successfully.");
        } catch (Exception e) {
            logError("Failed to initialize ChatFlow: " + e.getMessage());
        }
    }

    @SimpleEvent(description = "Triggered when an error or log message occurs.")
    public void ErrorOccurred(String message) {
        EventDispatcher.dispatchEvent(this, "ErrorOccurred", message);
    }

    @SimpleEvent(description = "Triggered when a message is clicked, returning ID, message content, timestamp, and click coordinates x and y.")
    public void MessageClicked(String id, String message, String timestamp, int x, int y) {
        EventDispatcher.dispatchEvent(this, "MessageClicked", id, message, timestamp, x, y);
    }

    @SimpleFunction(description = "Enables or disables logging mode.")
    public void EnableLogging(boolean enable) {
        loggingEnabled = enable;
        logMessage(loggingEnabled ? "Logging enabled." : "Logging disabled.");
    }

    @SimpleFunction(description = "Adds a message to the Chat View with styling options, image, timestamp, and corner radius.")
    public void AddMessage(final String message, final boolean isUser, @Asset final String imagePath, final boolean showTimestamp,
                           final int fontColor, final int backgroundColor, final float fontSize, final boolean bold, final boolean italic, final float cornerRadius) {
        
        final String id = UUID.randomUUID().toString();
        final Date timestamp = new Date();
        final String timestampString = DateFormat.format("hh:mm a", timestamp).toString();

        chatLayout.post(new Runnable() {
            @Override
            public void run() {
                try {
                    LinearLayout messageLayout = new LinearLayout(context);
                    messageLayout.setOrientation(LinearLayout.HORIZONTAL);
                    messageLayout.setPadding(10, 10, 10, 10);
                    messageLayout.setGravity(isUser ? Gravity.END : Gravity.START);

                    ImageView profileImage = null;
                    if (imagePath != null && !imagePath.isEmpty()) {
                        profileImage = createProfileImageView(imagePath);
                    }

                    LinearLayout bubbleLayout = new LinearLayout(context);
                    bubbleLayout.setOrientation(LinearLayout.VERTICAL);
                    bubbleLayout.setPadding(10, 10, 10, 10);

                    LinearLayout.LayoutParams bubbleLayoutParams = new LinearLayout.LayoutParams(
                            0, 
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1.0f 
                    );
                    bubbleLayout.setLayoutParams(bubbleLayoutParams);

                    TextView messageView = createMessageView(message, fontColor, backgroundColor, fontSize, bold, italic, cornerRadius);
                    messageView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            int[] location = new int[2];
                            view.getLocationOnScreen(location);
                            int x = location[0];
                            int y = location[1];
                            MessageClicked(id, message, timestampString, x, y);
                        }
                    });

                    bubbleLayout.addView(messageView);
                    if (showTimestamp) {
                        TextView timestampView = createTimestampView(timestamp);
                        bubbleLayout.addView(timestampView);
                    }

                    if (isUser) {
                        messageLayout.addView(bubbleLayout);
                        if (profileImage != null) {
                            messageLayout.addView(profileImage);
                        }
                    } else {
                        if (profileImage != null) {
                            messageLayout.addView(profileImage);
                        }
                        messageLayout.addView(bubbleLayout);
                    }

                    chatLayout.addView(messageLayout);
                    scrollToBottom();

                    messagesMap.put(id, new MessageData(id, message, timestamp, messageView));
                    logMessage("Message added: " + message);

                    // Dispara o evento MessageAdded na thread principal de forma segura
                    container.$form().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                MessageAdded(id, message, timestampString);
                            } catch (Exception e) {
                                logError("Error firing MessageAdded event: " + e.getMessage());
                            }
                        }
                    });
                } catch (Exception e) {
                    logError("Error adding message: " + e.getMessage());
                }
            }
        });
    }

    @SimpleEvent(description = "Triggered when a message is added, returning the ID, content of the message, and timestamp.")
    public void MessageAdded(String id, String message, String timestamp) {
        EventDispatcher.dispatchEvent(this, "MessageAdded", id, message, timestamp);
    }

    @SimpleFunction(description = "Sets message margin and padding.")
    public void SetMessageMarginPadding(int left, int top, int right, int bottom) {
        chatLayout.setPadding(left, top, right, bottom);
    }

    @SimpleFunction(description = "Displays a floating menu at X and Y coordinates with a list of options and a tag.")
    public void ShowFloatingMenu(int x, int y, YailList items, final String tag) {
        final PopupMenu popupMenu = new PopupMenu(context, chatLayout);
        popupMenu.getMenu().clear();
        for (Object item : items.toArray()) {
            popupMenu.getMenu().add(item.toString());
        }
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem menuItem) {
                FloatingMenuClicked(menuItem.getTitle().toString(), tag);
                return true;
            }
        });
        popupMenu.show();
    }

    @SimpleEvent(description = "Triggered when a floating menu item is selected, returning the selected item and the tag.")
    public void FloatingMenuClicked(String item, String tag) {
        EventDispatcher.dispatchEvent(this, "FloatingMenuClicked", item, tag);
    }

    @SimpleFunction(description = "Exports the entire conversation in JSON format.")
    public String ExportChatToJson() {
        try {
            List<HashMap<String, Object>> exportData = new ArrayList<>();
            
            for (MessageData data : messagesMap.values()) {
                HashMap<String, Object> messageMap = new HashMap<>();
                messageMap.put("id", data.id);
                messageMap.put("message", data.message);
                messageMap.put("timestamp", DateFormat.format("yyyy-MM-dd hh:mm a", data.timestamp).toString());
                exportData.add(messageMap);
            }
            
            String jsonString = new Gson().toJson(exportData);
            return jsonString;
        } catch (Exception e) {
            logError("Error exporting to JSON: " + e.getMessage());
            return "{}";
        }
    }

    @SimpleFunction(description = "Edits a message by ID.")
    public void EditMessage(String id, String newMessage) {
        MessageData messageData = messagesMap.get(id);
        if (messageData != null) {
            messageData.setMessage(newMessage);
            updateMessageView(id, newMessage);
            logMessage("Message edited: " + newMessage);
        } else {
            logError("Message ID not found.");
        }
    }

    @SimpleFunction(description = "Deletes a message by ID.")
    public void DeleteMessage(String id) {
        MessageData messageData = messagesMap.get(id);
        if (messageData != null) {
            View parentLayout = (View) messageData.getMessageView().getParent();
            
            // Verifica se o layout pai é realmente o messageLayout e tenta removê-lo do chatLayout
            if (parentLayout != null && parentLayout.getParent() == chatLayout) {
                chatLayout.removeView(parentLayout);
                messagesMap.remove(id); // Remove a mensagem do mapa após removê-la da visualização
                logMessage("Message deleted with ID: " + id);
            } else {
                logError("Failed to delete message: Parent layout not found in chatLayout.");
            }
        } else {
            logError("Message ID not found: " + id);
        }
    }


    @SimpleFunction(description = "Displays a customizable text input modal with a title and a tag.")
    public void ShowTextInput(String title, int titleFontColor, float titleFontSize, boolean titleBold, boolean titleItalic,
                              int fontColor, int backgroundColor, float fontSize, boolean bold, boolean italic, 
                              float cornerRadius, int borderColor, int borderWidth,
                              String buttonText, int buttonFontColor, int buttonBackgroundColor, 
                              float buttonCornerRadius, int buttonBorderColor, int buttonBorderWidth,
                              final String tag) {

        // Create title TextView
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(titleFontSize);
        titleView.setTextColor(titleFontColor);
        titleView.setPadding(0, 20, 0, 20);
        titleView.setGravity(Gravity.CENTER);

        // Set title font style
        int titleStyle = (titleBold ? 1 : 0) | (titleItalic ? 2 : 0);
        titleView.setTypeface(null, titleStyle);

        // Create EditText for user input
        final EditText editText = new EditText(context);
        editText.setTextSize(fontSize);
        editText.setTextColor(fontColor);

        int textStyle = (bold ? 1 : 0) | (italic ? 2 : 0);
        editText.setTypeface(null, textStyle);

        // Set background for EditText with GradientDrawable
        GradientDrawable backgroundDrawable = new GradientDrawable();
        backgroundDrawable.setColor(backgroundColor);
        backgroundDrawable.setCornerRadius(cornerRadius);
        backgroundDrawable.setStroke(borderWidth, borderColor);
        editText.setBackground(backgroundDrawable);

        // Create layout for input and button
        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(30, 30, 30, 30);

        // Add title and EditText to layout
        layout.addView(titleView);
        layout.addView(editText);

        // Button to submit the input text with customized styling
        Button submitButton = new Button(context);
        submitButton.setText(buttonText);
        submitButton.setTextColor(buttonFontColor);

        GradientDrawable buttonBackground = new GradientDrawable();
        buttonBackground.setColor(buttonBackgroundColor);
        buttonBackground.setCornerRadius(buttonCornerRadius);
        buttonBackground.setStroke(buttonBorderWidth, buttonBorderColor);
        submitButton.setBackground(buttonBackground);

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String userInput = editText.getText().toString();
                TextInputSubmitted(userInput, tag); // Pass the tag along with the input text
                ((ViewGroup) layout.getParent()).removeView(layout);
            }
        });

        layout.addView(submitButton);
        chatLayout.addView(layout);
        scrollToBottom();
    }

    @SimpleEvent(description = "Event triggered when the user submits the entered text, returning the text and tag.")
    public void TextInputSubmitted(String text, String tag) {
        EventDispatcher.dispatchEvent(this, "TextInputSubmitted", text, tag);
    }

    @SimpleFunction(description = "Copies text to the clipboard.")
    public void CopyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Copied Text", text);
        clipboard.setPrimaryClip(clip);
        logMessage("Text copied to clipboard.");
    }

    private void updateMessageView(String id, String newMessage) {
        MessageData messageData = messagesMap.get(id);
        if (messageData != null) {
            TextView messageView = messageData.getMessageView();
            messageView.setText(newMessage);
        }
    }

    private TextView createMessageView(String message, int fontColor, int backgroundColor, float fontSize, boolean bold, boolean italic, float cornerRadius) {
        TextView messageView = new TextView(context);
        messageView.setText(message);
        messageView.setTextSize(fontSize);
        messageView.setPadding(20, 15, 20, 15);
        messageView.setTextColor(fontColor);
        messageView.setTypeface(null, (bold ? 1 : 0) | (italic ? 2 : 0));

        GradientDrawable bubbleBackground = new GradientDrawable();
        bubbleBackground.setCornerRadius(cornerRadius);
        bubbleBackground.setColor(backgroundColor);
        bubbleBackground.setStroke(2, Color.LTGRAY);
        messageView.setBackground(bubbleBackground);

        return messageView;
    }

    private TextView createTimestampView(Date timestamp) {
        TextView timestampView = new TextView(context);
        timestampView.setText(DateFormat.format("hh:mm a", timestamp));
        timestampView.setTextSize(10);
        timestampView.setTextColor(Color.GRAY);
        timestampView.setPadding(10, 5, 10, 0);
        return timestampView;
    }
    
    private ImageView createProfileImageView(String imagePath) {
        try {
            Drawable drawable = MediaUtil.getBitmapDrawable(container.$form(), imagePath);
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();

            ImageView profileImage = new ImageView(context);
            profileImage.setImageBitmap(bitmap);

            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(100, 100);
            layoutParams.gravity = Gravity.CENTER_VERTICAL;
            profileImage.setLayoutParams(layoutParams);

            GradientDrawable circularMask = new GradientDrawable();
            circularMask.setShape(GradientDrawable.OVAL);
            circularMask.setColor(Color.TRANSPARENT);
            profileImage.setBackground(circularMask);
            profileImage.setClipToOutline(true);

            profileImage.setElevation(10);

            return profileImage;
        } catch (IOException e) {
            logError("Failed to load image: " + e.getMessage());
            return null;
        }
    }

    private void scrollToBottom() {
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    private void logMessage(String message) {
        if (loggingEnabled) {
            ErrorOccurred(message);
        }
    }

    private void logError(String error) {
        if (loggingEnabled) {
            ErrorOccurred(error);
        }
    }

    private static class MessageData {
        String id;
        String message;
        Date timestamp;
        TextView messageView;

        MessageData(String id, String message, Date timestamp, TextView messageView) {
            this.id = id;
            this.message = message;
            this.timestamp = timestamp;
            this.messageView = messageView;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public TextView getMessageView() {
            return messageView;
        }
    }
}
