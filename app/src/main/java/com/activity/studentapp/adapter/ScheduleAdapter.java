package com.activity.studentapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.activity.studentapp.R;
import com.activity.studentapp.model.Schedule;

import java.util.List;

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder> {

    private List<Schedule> schedules;

    public ScheduleAdapter(List<Schedule> schedules) {
        this.schedules = schedules;
    }

    public void updateSchedules(List<Schedule> schedules) {
        this.schedules = schedules;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ScheduleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_schedule, parent, false);
        return new ScheduleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScheduleViewHolder holder, int position) {
        Schedule schedule = schedules.get(position);
        holder.tvSubjectName.setText(schedule.getSubjectName());
        holder.tvInstructor.setText("Instructor: " + schedule.getInstructorName());
        holder.tvTime.setText("Time: " + schedule.getTimeFrame());
    }

    @Override
    public int getItemCount() {
        return schedules != null ? schedules.size() : 0;
    }

    static class ScheduleViewHolder extends RecyclerView.ViewHolder {
        TextView tvSubjectName, tvInstructor, tvTime;

        public ScheduleViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSubjectName = itemView.findViewById(R.id.tvSubjectName);
            tvInstructor = itemView.findViewById(R.id.tvInstructor);
            tvTime = itemView.findViewById(R.id.tvTime);
        }
    }
}