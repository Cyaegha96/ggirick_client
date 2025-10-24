package com.kedu.ggirick_client_backend.services.approval;

import com.kedu.ggirick_client_backend.dto.approval.ApprovalDTO;
import com.kedu.ggirick_client_backend.dto.approval.ApprovalHistoryDTO;
import com.kedu.ggirick_client_backend.dto.approval.ApprovalLineDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static com.kedu.ggirick_client_backend.config.ApprovalConfig.*;

@Service
@RequiredArgsConstructor
public class ApprovalProcessService {
    private final ApprovalHistoryService approvalHistoryService;
    private final ApprovalLineService approvalLineService;
    private final ApprovalService approvalService;
    private final ApprovalFilesService approvalFilesService;

    // 결재 상태 변경 로직
    @Transactional
    public void processApproval(ApprovalHistoryDTO approvalHistoryInfo, String userId) throws Exception {
        // 요청사용자의 id를 next_assigner로 가지고 approvalId를 approval_id로 가지는 approval_line을 조회
        List<ApprovalLineDTO> approvalLineList = approvalLineService.getByNextAssignerAndApprovalId(userId, approvalHistoryInfo.getApprovalId());

        // 사용자에 대한 결재선이 없거나 문서가 이미 승인또는반려된 경우 throws
        if (approvalLineList.isEmpty() || approvalService.getById(approvalHistoryInfo.getApprovalId()).getAssignedAt() != null) {
            throw new Exception();
        }

        // 조회된 approval_line의 assigner와 approvalId에 해당하는 approval_history를 조회
        // 조회된 내용이 있거나 approval_line의 assigner가 null인 경우, 결재 상태 변경
        // 조회 시, 대리결재자가 있을 수 있으므로 List로 조회하여 비교
        for (ApprovalLineDTO approvalLine : approvalLineList) {
            if (approvalLine.getAssigner() == null ||
                    approvalHistoryService.getByAssignerAndApprovalId(approvalLine.getAssigner(), approvalHistoryInfo.getApprovalId()) != null) {
                approvalHistoryInfo.setAssigner(userId);
                approvalHistoryService.insert(approvalHistoryInfo);

                // 결재 상태 변경이 반려인 경우, 결재문서의 상태를 반려로 변경
                if (approvalHistoryInfo.getTypeId() == TYPE_REJECT) {
                    approvalService.updateType(TYPE_REJECT, approvalHistoryInfo.getApprovalId());
                }

                // 만약 요청사용자의 id를 assigner로 가지는 approval_line의 next_assigner가 null인 경우, 최종 결재자를 뜻함
                // 결재 상태 변경이 의견 또는 취소가 아닌 경우, 결재문서의 상태를 승인로 변경
                // 결재 대리인은 결재선에 이미 포함된 인원은 설정할 수 없다는 전제
                if (approvalHistoryInfo.getTypeId() == TYPE_APPROVE) {
                    List<ApprovalLineDTO> nextApprovalLineLit = approvalLineService.getByAssignerAndApprovalId(userId, approvalHistoryInfo.getApprovalId());
                    for (ApprovalLineDTO nextApprovalLine : nextApprovalLineLit) {
                        if (nextApprovalLine.getNextAssigner() == null) {
                            approvalService.updateType(TYPE_APPROVE, approvalHistoryInfo.getApprovalId());

                            // 문서 승인 후, 문서 종류에 따라 다른 로직 수행
                            // TODO: 문서 종류에 대한 처리 - config/ApprovalConfig에 정의
                            switch(approvalService.getById(approvalHistoryInfo.getApprovalId()).getDocTypeCode()) {
                                case DOC_TYPE_CONTACT -> {
                                    break;
                                }
                                case DOC_TYPE_VACATION -> {
                                    break;
                                }
                            }
                        }
                    }
                }
                return;
            }
        }
        throw new Exception();
    }

    // 결재 문서 생성
    @Transactional
    public void processInsertApproval(ApprovalDTO approvalInfo, List<MultipartFile> files, List<ApprovalLineDTO> approvalLineInfoList) throws Exception {
        int approvalId = approvalService.insert(approvalInfo);
        if (files != null) {
            approvalFilesService.insertFileInfo(files, approvalId);
        }
        approvalLineService.insert(approvalLineInfoList, approvalId);
    }

    // 결재 문서 수정
    @Transactional
    public void processUpdateApproval(ApprovalDTO approvalInfo, List<MultipartFile> files, List<ApprovalLineDTO> approvalLineInfoList, int approvalId, String userId) throws Exception {
        ApprovalDTO selectedApproval = approvalService.getById(approvalId);

        if (selectedApproval.getWriter().equals(userId)
                && selectedApproval.getAssignedAt() == null) {
            approvalInfo.setId(approvalId);
            approvalService.update(approvalInfo);
            if (files != null) {
                approvalFilesService.insertFileInfo(files, approvalId);
            }
            if (approvalLineInfoList != null) {
                approvalLineService.deleteByApprovalId(approvalId);
                approvalLineService.insert(approvalLineInfoList, approvalId);
            }
        }
    }
}
