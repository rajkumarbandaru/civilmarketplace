package com.civileng.marketplace.user.service;

import com.civileng.marketplace.user.model.Address;
import com.civileng.marketplace.user.model.UserProfile;
import com.civileng.marketplace.user.repository.AddressRepository;
import com.civileng.marketplace.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserProfileRepository userProfileRepository;
    private final AddressRepository addressRepository;

    private static final int MAX_ADDRESSES_PER_USER = 10;

    @Transactional
    public UserProfile createProfile(Long userId, UserProfile profile) {
        if (userProfileRepository.existsByUserId(userId)) {
            throw new IllegalArgumentException("Profile already exists for user");
        }
        profile.setUserId(userId);
        UserProfile saved = userProfileRepository.save(profile);
        log.info("Profile created for user: {}", userId);
        return saved;
    }

    @Transactional(readOnly = true)
    public UserProfile getProfile(Long userId) {
        return userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found"));
    }

    @Transactional
    public UserProfile updateProfile(Long userId, UserProfile updatedProfile) {
        UserProfile profile = getProfile(userId);
        if (updatedProfile.getDateOfBirth() != null)
            profile.setDateOfBirth(updatedProfile.getDateOfBirth());
        if (updatedProfile.getGender() != null)
            profile.setGender(updatedProfile.getGender());
        if (updatedProfile.getAddressLine1() != null)
            profile.setAddressLine1(updatedProfile.getAddressLine1());
        if (updatedProfile.getAddressLine2() != null)
            profile.setAddressLine2(updatedProfile.getAddressLine2());
        if (updatedProfile.getCity() != null)
            profile.setCity(updatedProfile.getCity());
        if (updatedProfile.getState() != null)
            profile.setState(updatedProfile.getState());
        if (updatedProfile.getPincode() != null)
            profile.setPincode(updatedProfile.getPincode());
        if (updatedProfile.getBio() != null)
            profile.setBio(updatedProfile.getBio());
        if (updatedProfile.getLanguages() != null)
            profile.setLanguages(updatedProfile.getLanguages());
        if (updatedProfile.getExperienceYears() != null)
            profile.setExperienceYears(updatedProfile.getExperienceYears());
        if (updatedProfile.getHourlyRate() != null)
            profile.setHourlyRate(updatedProfile.getHourlyRate());
        if (updatedProfile.getIsAvailable() != null)
            profile.setIsAvailable(updatedProfile.getIsAvailable());
        if (updatedProfile.getDateOfBirth() != null)
            profile.setDateOfBirth(updatedProfile.getDateOfBirth());

        log.info("Profile updated for user: {}", userId);
        return userProfileRepository.save(profile);
    }

    @Transactional
    public Address addAddress(Long userId, Address address) {
        long addressCount = addressRepository.countByUserId(userId);
        if (addressCount >= MAX_ADDRESSES_PER_USER) {
            throw new IllegalArgumentException(
                    "Maximum " + MAX_ADDRESSES_PER_USER + " addresses allowed");
        }

        address.setUserId(userId);
        if (address.getIsDefault() || addressCount == 0) {
            clearDefaultAddress(userId);
            address.setIsDefault(true);
        }

        Address saved = addressRepository.save(address);
        log.info("Address added for user: {}", userId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Address> getUserAddresses(Long userId) {
        return addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
    }

    @Transactional
    public Address updateAddress(Long userId, Long addressId, Address updatedAddress) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Address not found"));

        if (updatedAddress.getLabel() != null)
            address.setLabel(updatedAddress.getLabel());
        if (updatedAddress.getAddressLine1() != null)
            address.setAddressLine1(updatedAddress.getAddressLine1());
        if (updatedAddress.getAddressLine2() != null)
            address.setAddressLine2(updatedAddress.getAddressLine2());
        if (updatedAddress.getLandmark() != null)
            address.setLandmark(updatedAddress.getLandmark());
        if (updatedAddress.getCity() != null)
            address.setCity(updatedAddress.getCity());
        if (updatedAddress.getState() != null)
            address.setState(updatedAddress.getState());
        if (updatedAddress.getPincode() != null)
            address.setPincode(updatedAddress.getPincode());
        if (updatedAddress.getLatitude() != null)
            address.setLatitude(updatedAddress.getLatitude());
        if (updatedAddress.getLongitude() != null)
            address.setLongitude(updatedAddress.getLongitude());
        if (updatedAddress.getIsDefault() != null && updatedAddress.getIsDefault()) {
            clearDefaultAddress(userId);
            address.setIsDefault(true);
        }

        log.info("Address updated for user: {}", userId);
        return addressRepository.save(address);
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        addressRepository.deleteByIdAndUserId(addressId, userId);
        log.info("Address deleted for user: {}", userId);
    }

    @Transactional
    public void setDefaultAddress(Long userId, Long addressId) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Address not found"));
        clearDefaultAddress(userId);
        address.setIsDefault(true);
        addressRepository.save(address);
    }

    private void clearDefaultAddress(Long userId) {
        List<Address> addresses = addressRepository
                .findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
        addresses.forEach(addr -> addr.setIsDefault(false));
        addressRepository.saveAll(addresses);
    }
}
