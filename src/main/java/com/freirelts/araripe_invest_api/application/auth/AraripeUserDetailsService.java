package com.freirelts.araripe_invest_api.application.auth;

import com.freirelts.araripe_invest_api.infrastructure.persistence.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AraripeUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	AraripeUserDetailsService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		return userRepository.findByEmailIgnoreCase(username)
				.map(AraripeUserDetails::new)
				.orElseThrow(() -> new UsernameNotFoundException("Invalid credentials."));
	}
}
