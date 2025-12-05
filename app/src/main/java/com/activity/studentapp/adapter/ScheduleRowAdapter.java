package com.activity.studentapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.activity.studentapp.R;
import com.activity.studentapp.model.ScheduleRow;

import java.util.List;

public class ScheduleRowAdapter extends RecyclerView.Adapter<ScheduleRowAdapter.ScheduleRowViewHolder> {

    private List<ScheduleRow> rows;

    public ScheduleRowAdapter(List<ScheduleRow> rows) {
        this.rows = rows;
    }

    public void updateRows(List<ScheduleRow> rows) {
        this.rows = rows;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ScheduleRowViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_schedule_row, parent, false);
        return new ScheduleRowViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScheduleRowViewHolder holder, int position) {
        ScheduleRow row = rows.get(position);
        holder.tvTime.setText(row.getTime());
        String[] subjects = row.getSubjects();
        holder.tvMon.setText(subjects[0] != null ? subjects[0] : "");
        holder.tvTue.setText(subjects[1] != null ? subjects[1] : "");
        holder.tvWed.setText(subjects[2] != null ? subjects[2] : "");
        holder.tvThu.setText(subjects[3] != null ? subjects[3] : "");
        holder.tvFri.setText(subjects[4] != null ? subjects[4] : "");

        // Set background for cells with subjects
        setCellBackground(holder.tvMon, subjects[0]);
        setCellBackground(holder.tvTue, subjects[1]);
        setCellBackground(holder.tvWed, subjects[2]);
        setCellBackground(holder.tvThu, subjects[3]);
        setCellBackground(holder.tvFri, subjects[4]);
    }

    private void setCellBackground(TextView textView, String subject) {
        if (subject != null && !subject.isEmpty()) {
            textView.setBackgroundColor(textView.getContext().getResources().getColor(R.color.green));
            textView.setTextColor(textView.getContext().getResources().getColor(android.R.color.white));
        } else {
            textView.setBackgroundResource(R.drawable.cell_border);
            textView.setTextColor(textView.getContext().getResources().getColor(android.R.color.black));
        }
    }

    @Override
    public int getItemCount() {
        return rows != null ? rows.size() : 0;
    }

    static class ScheduleRowViewHolder extends RecyclerView.ViewHolder {
        TextView tvTime, tvMon, tvTue, tvWed, tvThu, tvFri;

        public ScheduleRowViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvMon = itemView.findViewById(R.id.tvMon);
            tvTue = itemView.findViewById(R.id.tvTue);
            tvWed = itemView.findViewById(R.id.tvWed);
            tvThu = itemView.findViewById(R.id.tvThu);
            tvFri = itemView.findViewById(R.id.tvFri);
        }
    }
}