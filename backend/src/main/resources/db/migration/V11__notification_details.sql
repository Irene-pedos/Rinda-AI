-- code lets the app show its own translated text. plot_id opens the right screen.
ALTER TABLE notifications
    ADD COLUMN code    VARCHAR(30) NOT NULL,
    ADD COLUMN plot_id BIGINT,
    ADD COLUMN is_demo BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT fk_notifications_plot FOREIGN KEY (plot_id) REFERENCES plots (id);
