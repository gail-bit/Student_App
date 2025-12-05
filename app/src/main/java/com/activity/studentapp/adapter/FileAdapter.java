package com.activity.studentapp.adapter;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.activity.studentapp.R;

import java.util.List;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {

    private List<Uri> fileUris;
    private OnFileRemoveListener listener;

    public interface OnFileRemoveListener {
        void onFileRemove(int position);
    }

    public FileAdapter(List<Uri> fileUris, OnFileRemoveListener listener) {
        this.fileUris = fileUris;
        this.listener = listener;
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        Uri uri = fileUris.get(position);
        String fileName = getFileNameFromUri(uri);
        holder.fileNameTextView.setText(fileName);

        holder.removeFileButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFileRemove(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return fileUris.size();
    }

    private String getFileNameFromUri(Uri uri) {
        String path = uri.getPath();
        if (path != null) {
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash != -1) {
                return path.substring(lastSlash + 1);
            }
        }
        return "Unknown file";
    }

    public static class FileViewHolder extends RecyclerView.ViewHolder {
        TextView fileNameTextView;
        ImageButton removeFileButton;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            fileNameTextView = itemView.findViewById(R.id.fileNameTextView);
            removeFileButton = itemView.findViewById(R.id.removeFileButton);
        }
    }
}