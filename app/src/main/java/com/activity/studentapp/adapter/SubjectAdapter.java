package com.activity.studentapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.activity.studentapp.R;
import com.activity.studentapp.model.Subject;

import java.util.List;

public class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.SubjectViewHolder> {
    private List<Subject> subjects;

    public SubjectAdapter(List<Subject> subjects) {
        this.subjects = subjects;
    }

    @NonNull
    @Override
    public SubjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_subject, parent, false);
        return new SubjectViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SubjectViewHolder holder, int position) {
        Subject subject = subjects.get(position);
        holder.bind(subject);
    }

    @Override
    public int getItemCount() {
        return subjects != null ? subjects.size() : 0;
    }

    public void updateSubjects(List<Subject> newSubjects) {
        this.subjects = newSubjects;
        notifyDataSetChanged();
    }

    static class SubjectViewHolder extends RecyclerView.ViewHolder {
        private final TextView subjectNameText;
        private final TextView subjectCodeText;
        private final TextView subjectScheduleText;
        private final TextView instructorNameText;
        private final TextView roomText;

        public SubjectViewHolder(@NonNull View itemView) {
            super(itemView);
            subjectNameText = itemView.findViewById(R.id.subjectNameText);
            subjectCodeText = itemView.findViewById(R.id.subjectCodeText);
            subjectScheduleText = itemView.findViewById(R.id.subjectScheduleText);
            instructorNameText = itemView.findViewById(R.id.instructorNameText);
            roomText = itemView.findViewById(R.id.roomText);
        }

        public void bind(Subject subject) {
            Log.d("SubjectAdapter", "Binding subject: " + subject.getName() + ", code: " + subject.getCode());
            subjectNameText.setText(subject.getName() != null ? subject.getName() : "No Name");
            subjectCodeText.setText(subject.getCode() != null ? subject.getCode() : "No Code");
            subjectScheduleText.setText(subject.getSchedule() != null ? subject.getSchedule() : "No Schedule");
            instructorNameText.setText(subject.getInstructor() != null ? subject.getInstructor() : "No Instructor");
            roomText.setText(subject.getRoom() != null ? subject.getRoom() : "No Room");
        }
    }
}
