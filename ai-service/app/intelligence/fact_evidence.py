from app.intelligence.deadline_normalizer import DeadlineNormalizer
from app.schemas import Problem, Segment, Speaker, Task, validate_fact_sources


def normalized(text: str) -> str:
    return " ".join(text.casefold().split())


def validate_fact_evidence(
    tasks: list[Task],
    problems: list[Problem],
    segments: list[Segment],
    speakers: list[Speaker],
) -> None:
    errors = []
    try:
        validate_fact_sources(tasks, problems, segments)
    except ValueError as error:
        errors.append(str(error))
    by_id = {segment.id: segment for segment in segments}
    names = {speaker.id: speaker.name for speaker in speakers}
    for fact in [*tasks, *problems]:
        if len(fact.source_segment_ids) != len(set(fact.source_segment_ids)):
            errors.append(f"{fact.id}: sourceSegmentIds must be unique")
        sources = [
            by_id[identifier]
            for identifier in fact.source_segment_ids
            if identifier in by_id
        ]
        role_name = fact.assigner_name if isinstance(fact, Task) else fact.reported_by
        if role_name is not None and not any(
            normalized(names.get(source.speaker_id) or "") == normalized(role_name)
            for source in sources
        ):
            errors.append(
                f"{fact.id}: {role_name} must be the speaker of a cited source; include the actual instruction/report segment or use null for the role"
            )
        if isinstance(fact, Task):
            if fact.deadline_raw is not None:
                if not DeadlineNormalizer().is_deadline_phrase(fact.deadline_raw):
                    errors.append(
                        f"{fact.id}: deadlineRaw has no temporal meaning; use null if no deadline is stated"
                    )
                elif not any(
                    normalized(fact.deadline_raw) in normalized(source.text)
                    for source in sources
                ):
                    errors.append(
                        f"{fact.id}: deadlineRaw={fact.deadline_raw!r} must be copied from a cited source"
                    )
            if fact.assignee_name is not None and not any(
                normalized(fact.assignee_name) in normalized(source.text)
                or normalized(fact.assignee_name)
                == normalized(names.get(source.speaker_id) or "")
                for source in sources
            ):
                errors.append(
                    f"{fact.id}: assigneeName={fact.assignee_name!r} must occur in a cited source or identify its speaker; include the assignment context"
                )
        else:
            quoted_sources = [
                source
                for source in sources
                if normalized(fact.text) in normalized(source.text)
            ]
            if not quoted_sources:
                errors.append(
                    f"{fact.id}: problem text must be an EXACT excerpt from a cited segment, not a paraphrase"
                )
            elif role_name is not None and not any(
                normalized(names.get(source.speaker_id) or "") == normalized(role_name)
                for source in quoted_sources
            ):
                errors.append(
                    f"{fact.id}: reportedBy must be the speaker of the quoted problem excerpt"
                )
    if errors:
        raise ValueError("; ".join(errors))
