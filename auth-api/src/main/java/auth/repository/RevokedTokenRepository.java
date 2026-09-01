package auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import auth.model.RevokedToken;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {
}
