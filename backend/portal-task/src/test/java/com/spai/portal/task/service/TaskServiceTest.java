package com.spai.portal.task.service;

import java.util.Optional;
import com.spai.portal.common.BusinessException;
import com.spai.portal.task.domain.DeliveryTask;
import com.spai.portal.task.domain.TaskProgress;
import com.spai.portal.task.repository.DeliveryTaskRepository;
import com.spai.portal.task.repository.TaskProgressHistoryRepository;
import com.spai.portal.task.repository.TaskProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskServiceTest {
    private DeliveryTaskRepository tasks;
    private TaskProgressRepository progress;
    private TaskService service;

    @BeforeEach
    void setUp() {
        tasks = mock(DeliveryTaskRepository.class);
        progress = mock(TaskProgressRepository.class);
        service = new TaskService(tasks, progress, mock(TaskProgressHistoryRepository.class));
    }

    @Test
    void memberCannotUpdateAnotherTeam() {
        assertThatThrownBy(() -> service.update("task", "team-b", "进行中", 0, "user", "team-a", false))
            .isInstanceOf(BusinessException.class)
            .hasMessage("只能更新所属团队进度");
    }

    @Test
    void staleLockVersionIsRejected() {
        DeliveryTask task = new DeliveryTask(); task.setId("task");
        TaskProgress saved = new TaskProgress(); saved.setId("progress"); saved.setTaskId("task"); saved.setTeamId("team"); saved.setLockVersion(3);
        when(tasks.findById("task")).thenReturn(Optional.of(task));
        when(progress.findByTaskIdAndTeamId("task", "team")).thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> service.update("task", "team", "已完成", 2, "user", "team", false))
            .isInstanceOf(BusinessException.class)
            .hasMessage("任务进度已被其他用户更新");
    }
}
