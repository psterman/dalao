// Generated by view binder compiler. Do not edit!
package com.example.aifloatingball.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.viewbinding.ViewBinding;
import androidx.viewbinding.ViewBindings;
import com.example.aifloatingball.R;
import java.lang.NullPointerException;
import java.lang.Override;
import java.lang.String;

public final class CardSearchEngineBinding implements ViewBinding {
  @NonNull
  private final CardView rootView;

  @NonNull
  public final ImageView engineIcon;

  @NonNull
  public final TextView engineName;

  @NonNull
  public final LinearLayout titleBar;

  @NonNull
  public final FrameLayout webviewContainer;

  private CardSearchEngineBinding(@NonNull CardView rootView, @NonNull ImageView engineIcon,
      @NonNull TextView engineName, @NonNull LinearLayout titleBar,
      @NonNull FrameLayout webviewContainer) {
    this.rootView = rootView;
    this.engineIcon = engineIcon;
    this.engineName = engineName;
    this.titleBar = titleBar;
    this.webviewContainer = webviewContainer;
  }

  @Override
  @NonNull
  public CardView getRoot() {
    return rootView;
  }

  @NonNull
  public static CardSearchEngineBinding inflate(@NonNull LayoutInflater inflater) {
    return inflate(inflater, null, false);
  }

  @NonNull
  public static CardSearchEngineBinding inflate(@NonNull LayoutInflater inflater,
      @Nullable ViewGroup parent, boolean attachToParent) {
    View root = inflater.inflate(R.layout.card_search_engine, parent, false);
    if (attachToParent) {
      parent.addView(root);
    }
    return bind(root);
  }

  @NonNull
  public static CardSearchEngineBinding bind(@NonNull View rootView) {
    // The body of this method is generated in a way you would not otherwise write.
    // This is done to optimize the compiled bytecode for size and performance.
    int id;
    missingId: {
      id = R.id.engine_icon;
      ImageView engineIcon = ViewBindings.findChildViewById(rootView, id);
      if (engineIcon == null) {
        break missingId;
      }

      id = R.id.engine_name;
      TextView engineName = ViewBindings.findChildViewById(rootView, id);
      if (engineName == null) {
        break missingId;
      }

      id = R.id.title_bar;
      LinearLayout titleBar = ViewBindings.findChildViewById(rootView, id);
      if (titleBar == null) {
        break missingId;
      }

      id = R.id.webview_container;
      FrameLayout webviewContainer = ViewBindings.findChildViewById(rootView, id);
      if (webviewContainer == null) {
        break missingId;
      }

      return new CardSearchEngineBinding((CardView) rootView, engineIcon, engineName, titleBar,
          webviewContainer);
    }
    String missingId = rootView.getResources().getResourceName(id);
    throw new NullPointerException("Missing required view with ID: ".concat(missingId));
  }
}
