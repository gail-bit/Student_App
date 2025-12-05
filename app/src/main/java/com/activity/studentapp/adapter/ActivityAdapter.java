package com.activity.studentapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.activity.studentapp.R;
import com.activity.studentapp.model.Activity;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ActivityAdapter extends RecyclerView.Adapter<ActivityAdapter.ActivityViewHolder> {

    private List<Activity> activities;
    private OnActivityClickListener listener;

    public interface OnActivityClickListener {
        void onActivityClick(Activity activity);
    }

    public ActivityAdapter(List<Activity> activities, OnActivityClickListener listener) {
        this.activities = activities;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ActivityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_activity, parent, false);
        return new ActivityViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ActivityViewHolder holder, int position) {
        Activity activity = activities.get(position);
        holder.bind(activity, listener);
    }

    @Override
    public int getItemCount() {
        return activities != null ? activities.size() : 0;
    }

    public void updateActivities(List<Activity> newActivities) {
        this.activities = new ArrayList<>(newActivities);
        notifyDataSetChanged();
    }

    static class ActivityViewHolder extends RecyclerView.ViewHolder {
        private TextView tvActivityTitle;
        private TextView tvInstructorName;

        public ActivityViewHolder(@NonNull View itemView) {
            super(itemView);
            tvActivityTitle = itemView.findViewById(R.id.tvActivityTitle);
            tvInstructorName = itemView.findViewById(R.id.tvInstructorName);
        }

        public void bind(Activity activity, OnActivityClickListener listener) {
            tvActivityTitle.setText(activity.getTitle() != null ? activity.getTitle() : "Untitled Activity");
            tvInstructorName
                    .setText(activity.getInstructorName() != null ? "Instructor: " + activity.getInstructorName()
                            : "Unknown Instructor");

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onActivityClick(activity);
                }
            });
        }
    }
}