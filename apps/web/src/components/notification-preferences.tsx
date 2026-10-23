'use client';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { ErrorState } from './common';
type Preferences = {
  digest: boolean;
  reminders: boolean;
  changes: boolean;
  reminder_days: number[];
};
export function NotificationPreferences() {
  const client = useQueryClient();
  const query = useQuery({
    queryKey: ['notification-preferences'],
    queryFn: () => api<Preferences>('/notifications/preferences'),
  });
  const mutation = useMutation({
    mutationFn: (value: Preferences) =>
      api('/notifications/preferences', {
        method: 'PUT',
        body: JSON.stringify({ ...value, reminderDays: value.reminder_days }),
      }),
    onSuccess: () => client.invalidateQueries({ queryKey: ['notification-preferences'] }),
  });
  if (query.error) return <ErrorState error={query.error} />;
  if (!query.data) return null;
  return (
    <section className="panel">
      <h3>Email preferences</h3>
      <p className="muted">
        Opt into the updates that help you. Delivery requires your administrator to configure SMTP
        and enable notifications.
      </p>
      {(['digest', 'reminders', 'changes'] as const).map((key) => (
        <label key={key}>
          <input
            type="checkbox"
            checked={query.data[key]}
            disabled={mutation.isPending}
            onChange={(e) => mutation.mutate({ ...query.data!, [key]: e.target.checked })}
          />
          {key === 'digest'
            ? 'Daily digest of new relevant matches'
            : key === 'reminders'
              ? 'Deadline reminders'
              : 'Confirmed deadline changes and cancellations'}
        </label>
      ))}
      <fieldset>
        <legend>Days before the deadline</legend>
        {[14, 7, 3, 1].map((day) => (
          <label key={day}>
            <input
              type="checkbox"
              checked={query.data.reminder_days.includes(day)}
              disabled={mutation.isPending}
              onChange={(e) =>
                mutation.mutate({
                  ...query.data!,
                  reminder_days: e.target.checked
                    ? [...query.data!.reminder_days, day]
                    : query.data!.reminder_days.filter((d) => d !== day),
                })
              }
            />
            {day} {day === 1 ? 'day' : 'days'}
          </label>
        ))}
      </fieldset>
      {mutation.error && <ErrorState error={mutation.error} />}
    </section>
  );
}
