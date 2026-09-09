package cn.superhuang.data.scalpel.business.panorama.web.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record PanoramaCandidateRequest(@NotNull UUID candidateId) {}
