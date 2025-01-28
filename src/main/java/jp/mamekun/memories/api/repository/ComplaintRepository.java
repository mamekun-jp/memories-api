package jp.mamekun.memories.api.repository;

import jp.mamekun.memories.api.model.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ComplaintRepository extends JpaRepository<Complaint, UUID> {
}
