export type MeetingStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
export type MeetingStage = 'PREPARING_MEDIA' | 'ANALYZING' | 'FINALIZING';
export type ExportFormat = 'pdf' | 'docx';
export interface MeetingLink { id: string; accessToken: string; title: string; createdAt: string; expiresAt: string | null }
export interface AcceptedMeeting extends MeetingLink { status: MeetingStatus }
export interface Speaker { id: string; name: string | null }
export interface Segment { id: string; speakerId: string | null; startMs: number; endMs: number; text: string; tags: ('TASK' | 'PROBLEM')[] }
export interface MeetingTask { id: string; text: string; assigneeName: string | null; assignerName: string | null; deadlineRaw: string | null; deadlineDate: string | null; sourceSegmentIds: string[] }
export interface MeetingProblem { id: string; text: string; reportedBy: string | null; sourceSegmentIds: string[] }
export interface MeetingResult { durationMs: number; summary: string; speakers: Speaker[]; segments: Segment[]; tasks: MeetingTask[]; problems: MeetingProblem[] }
export interface Meeting { id: string; title: string; startedAt: string | null; timeZone: string | null; createdAt: string; status: MeetingStatus; stage: MeetingStage | null; expiresAt: string | null; error: { code: string; message: string } | null; result: MeetingResult | null }
export interface UploadInput { file: File; source: 'UPLOAD' | 'MICROPHONE'; startedAt?: string; timeZone?: string }
export interface ProblemDetails { type?: string; title?: string; status?: number; detail?: string; code?: string; errors?: unknown[] }
