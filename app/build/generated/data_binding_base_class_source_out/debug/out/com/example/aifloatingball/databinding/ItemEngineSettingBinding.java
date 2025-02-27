// Generated by view binder compiler. Do not edit!
package com.example.aifloatingball.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;
import androidx.viewbinding.ViewBindings;
import com.example.aifloatingball.R;
import java.lang.NullPointerException;
import java.lang.Override;
import java.lang.String;

public final class ItemEngineSettingBinding implements ViewBinding {
  @NonNull
  private final LinearLayout rootView;

  @NonNull
  public final CheckBox checkbox;

  @NonNull
  public final ImageView dragHandle;

  @NonNull
  public final TextView engineDescription;

  @NonNull
  public final ImageView engineIcon;

  @NonNull
  public final TextView engineName;

  private ItemEngineSettingBinding(@NonNull LinearLayout rootView, @NonNull CheckBox checkbox,
      @NonNull ImageView dragHandle, @NonNull TextView engineDescription,
      @NonNull ImageView engineIcon, @NonNull TextView engineName) {
    this.rootView = rootView;
    this.checkbox = checkbox;
    this.dragHandle = dragHandle;
    this.engineDescription = engineDescription;
    this.engineIcon = engineIcon;
    this.engineName = engineName;
  }

  @Override
  @NonNull
  public LinearLayout getRoot() {
    return rootView;
  }

  @NonNull
  public static ItemEngineSettingBinding inflate(@NonNull LayoutInflater inflater) {
    return inflate(inflater, null, false);
  }

  @NonNull
  public static ItemEngineSettingBinding inflate(@NonNull LayoutInflater inflater,
      @Nullable ViewGroup parent, boolean attachToParent) {
    View root = inflater.inflate(R.layout.item_engine_setting, parent, false);
    if (attachToParent) {
      parent.addView(root);
    }
    return bind(root);
  }

  @NonNull
  public static ItemEngineSettingBinding bind(@NonNull View rootView) {
    // The body of this method is generated in a way you would not otherwise write.
    // This is done to optimize the compiled bytecode for size and performance.
    int id;
    missingId: {
      id = R.id.checkbox;
      CheckBox checkbox = ViewBindings.findChildViewById(rootView, id);
      if (checkbox == null) {
        break missingId;
      }

      id = R.id.drag_handle;
      ImageView dragHandle = ViewBindings.findChildViewById(rootView, id);
      if (dragHandle == null) {
        break missingId;
      }

      id = R.id.engine_description;
      TextView engineDescription = ViewBindings.findChildViewById(rootView, id);
      if (engineDescription == null) {
        break missingId;
      }

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

      return new ItemEngineSettingBinding((LinearLayout) rootView, checkbox, dragHandle,
          engineDescription, engineIcon, engineName);
    }
    String missingId = rootView.getResources().getResourceName(id);
    throw new NullPointerException("Missing required view with ID: ".concat(missingId));
  }
}
