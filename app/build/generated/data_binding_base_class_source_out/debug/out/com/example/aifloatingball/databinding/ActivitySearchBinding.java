// Generated by view binder compiler. Do not edit!
package com.example.aifloatingball.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewbinding.ViewBinding;
import androidx.viewbinding.ViewBindings;
import com.example.aifloatingball.R;
import com.example.aifloatingball.view.LetterIndexBar;
import java.lang.NullPointerException;
import java.lang.Override;
import java.lang.String;

public final class ActivitySearchBinding implements ViewBinding {
  @NonNull
  private final DrawerLayout rootView;

  @NonNull
  public final ImageButton btnClose;

  @NonNull
  public final ImageButton btnMenu;

  @NonNull
  public final ImageButton btnSearch;

  @NonNull
  public final DrawerLayout drawerLayout;

  @NonNull
  public final LetterIndexBar letterIndexBar;

  @NonNull
  public final TextView letterTitle;

  @NonNull
  public final LinearLayout previewEngineList;

  @NonNull
  public final LinearLayout searchBar;

  @NonNull
  public final EditText searchInput;

  @NonNull
  public final WebView webView;

  private ActivitySearchBinding(@NonNull DrawerLayout rootView, @NonNull ImageButton btnClose,
      @NonNull ImageButton btnMenu, @NonNull ImageButton btnSearch,
      @NonNull DrawerLayout drawerLayout, @NonNull LetterIndexBar letterIndexBar,
      @NonNull TextView letterTitle, @NonNull LinearLayout previewEngineList,
      @NonNull LinearLayout searchBar, @NonNull EditText searchInput, @NonNull WebView webView) {
    this.rootView = rootView;
    this.btnClose = btnClose;
    this.btnMenu = btnMenu;
    this.btnSearch = btnSearch;
    this.drawerLayout = drawerLayout;
    this.letterIndexBar = letterIndexBar;
    this.letterTitle = letterTitle;
    this.previewEngineList = previewEngineList;
    this.searchBar = searchBar;
    this.searchInput = searchInput;
    this.webView = webView;
  }

  @Override
  @NonNull
  public DrawerLayout getRoot() {
    return rootView;
  }

  @NonNull
  public static ActivitySearchBinding inflate(@NonNull LayoutInflater inflater) {
    return inflate(inflater, null, false);
  }

  @NonNull
  public static ActivitySearchBinding inflate(@NonNull LayoutInflater inflater,
      @Nullable ViewGroup parent, boolean attachToParent) {
    View root = inflater.inflate(R.layout.activity_search, parent, false);
    if (attachToParent) {
      parent.addView(root);
    }
    return bind(root);
  }

  @NonNull
  public static ActivitySearchBinding bind(@NonNull View rootView) {
    // The body of this method is generated in a way you would not otherwise write.
    // This is done to optimize the compiled bytecode for size and performance.
    int id;
    missingId: {
      id = R.id.btn_close;
      ImageButton btnClose = ViewBindings.findChildViewById(rootView, id);
      if (btnClose == null) {
        break missingId;
      }

      id = R.id.btn_menu;
      ImageButton btnMenu = ViewBindings.findChildViewById(rootView, id);
      if (btnMenu == null) {
        break missingId;
      }

      id = R.id.btn_search;
      ImageButton btnSearch = ViewBindings.findChildViewById(rootView, id);
      if (btnSearch == null) {
        break missingId;
      }

      DrawerLayout drawerLayout = (DrawerLayout) rootView;

      id = R.id.letter_index_bar;
      LetterIndexBar letterIndexBar = ViewBindings.findChildViewById(rootView, id);
      if (letterIndexBar == null) {
        break missingId;
      }

      id = R.id.letter_title;
      TextView letterTitle = ViewBindings.findChildViewById(rootView, id);
      if (letterTitle == null) {
        break missingId;
      }

      id = R.id.preview_engine_list;
      LinearLayout previewEngineList = ViewBindings.findChildViewById(rootView, id);
      if (previewEngineList == null) {
        break missingId;
      }

      id = R.id.search_bar;
      LinearLayout searchBar = ViewBindings.findChildViewById(rootView, id);
      if (searchBar == null) {
        break missingId;
      }

      id = R.id.search_input;
      EditText searchInput = ViewBindings.findChildViewById(rootView, id);
      if (searchInput == null) {
        break missingId;
      }

      id = R.id.web_view;
      WebView webView = ViewBindings.findChildViewById(rootView, id);
      if (webView == null) {
        break missingId;
      }

      return new ActivitySearchBinding((DrawerLayout) rootView, btnClose, btnMenu, btnSearch,
          drawerLayout, letterIndexBar, letterTitle, previewEngineList, searchBar, searchInput,
          webView);
    }
    String missingId = rootView.getResources().getResourceName(id);
    throw new NullPointerException("Missing required view with ID: ".concat(missingId));
  }
}
